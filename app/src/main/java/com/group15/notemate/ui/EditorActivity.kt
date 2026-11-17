package com.group15.notemate.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.group15.notemate.R
import com.group15.notemate.data.NoteExporter
import com.group15.notemate.databinding.ActivityEditorBinding
import com.group15.notemate.ml.TextHelpers
import com.group15.notemate.model.Deadline
import com.group15.notemate.model.Flashcard
import com.group15.notemate.model.Note
import com.group15.notemate.model.NoteCategory
import com.group15.notemate.model.SmartEntity
import com.group15.notemate.viewmodel.EditorViewModel
import java.util.Locale

/**
 * MVVM: View. Note editor: type a note, add text from Scan, Handwriting or Voice Dictation,
 * listen with Text-to-Speech, share or export as PDF, and use the ML tools (summary, translation, dates).
 */
class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private val viewModel: EditorViewModel by viewModels()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false

    /** The Scan and Handwriting screens return the text they recognized; it is added to the end of the note. */
    private val addText = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringExtra(EXTRA_TEXT)
        if (result.resultCode == RESULT_OK && !text.isNullOrBlank()) appendToNote(text)
    }

    /** Voice dictation returns spoken text from the system speech recognizer. */
    private val dictateText = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == RESULT_OK && !spoken.isNullOrBlank()) appendToNote(spoken)
    }

    /** System file picker to save the note as a PDF document. */
    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            viewModel.exportPdf(
                uri = uri,
                title = binding.editTitle.text.toString(),
                course = binding.editCourse.text.toString(),
                category = selectedCategory(),
                content = content()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        observeMessages(viewModel)
        initTextToSpeech()

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        setTitle(if (noteId == 0L) R.string.title_new_note else R.string.title_edit_note)
        viewModel.start(noteId)

        binding.chipScan.setOnClickListener { openForText(ScanActivity::class.java) }
        binding.chipHandwrite.setOnClickListener { openForText(HandwritingActivity::class.java) }
        binding.chipDictate.setOnClickListener { startVoiceDictation() }
        binding.chipSpeak.setOnClickListener { toggleReadAloud() }
        binding.chipSummarize.setOnClickListener {
            shakeIfEmpty()
            viewModel.summarize(content())
        }
        binding.chipTranslate.setOnClickListener {
            shakeIfEmpty()
            viewModel.translate(content())
        }
        binding.chipDeadlines.setOnClickListener {
            if (binding.editTitle.text.isNullOrBlank()) shakeIfEmpty()
            viewModel.findDeadlines(binding.editTitle.text.toString(), content())
        }
        binding.chipFlashcards.setOnClickListener { openFlashcards() }
        binding.editContent.doAfterTextChanged { updateWordCount() }
        binding.buttonUseTranslation.setOnClickListener {
            viewModel.translation.value?.let { binding.editContent.setText(it.text) }
            viewModel.clearTranslation()
        }
        onBackPressedDispatcher.addCallback(this) { saveAndClose() }

        viewModel.loaded.observe(this) { event -> event.getContentIfNotHandled()?.let(::showNote) }
        viewModel.pinned.observe(this) { invalidateOptionsMenu() }
        viewModel.isLoading.observe(this) { loading ->
            binding.progress.isVisible = loading
            // One ML tool at a time
            binding.chipSummarize.isEnabled = !loading
            binding.chipTranslate.isEnabled = !loading
            binding.chipDeadlines.isEnabled = !loading
        }
        viewModel.summary.observe(this) { summary ->
            binding.textSummary.text = summary
            binding.cardSummary.show(summary.isNotBlank())
        }
        viewModel.translation.observe(this) { translation ->
            if (translation != null) {
                binding.textTranslationTitle.text = getString(R.string.translation_title, translation.languageName)
                binding.textTranslation.text = translation.text
            }
            binding.cardTranslation.show(translation != null)
        }
        viewModel.deadlines.observe(this) { event -> event.getContentIfNotHandled()?.let(::showDates) }
        viewModel.closed.observe(this) { closed -> if (closed) finishAfterTransition() }
        viewModel.noteMissing.observe(this) { missing ->
            if (!missing) return@observe
            // A Toast, because a Snackbar would close with this screen
            Toast.makeText(this, R.string.error_note_missing, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    /** The pin is faded while the note is not pinned. */
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val pinned = viewModel.pinned.value == true
        menu.findItem(R.id.action_pin)?.apply {
            setTitle(if (pinned) R.string.unpin_note else R.string.pin_note)
            icon?.mutate()?.alpha = if (pinned) ALPHA_PINNED else ALPHA_UNPINNED
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_pin -> viewModel.togglePin()
            R.id.action_save -> saveAndClose()
            R.id.action_share -> shareNote()
            R.id.action_export_pdf -> promptExportPdf()
            R.id.action_delete -> confirmDelete()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** The Up arrow saves, like Back. */
    override fun onSupportNavigateUp(): Boolean {
        saveAndClose()
        return true
    }

    private fun content() = binding.editContent.text.toString()

    private fun selectedCategory(): String = when (binding.chipGroupCategory.checkedChipId) {
        R.id.chip_cat_lecture -> NoteCategory.LECTURE.key
        R.id.chip_cat_assignment -> NoteCategory.ASSIGNMENT.key
        R.id.chip_cat_exam -> NoteCategory.EXAM.key
        R.id.chip_cat_urgent -> NoteCategory.URGENT.key
        else -> ""
    }

    private fun showNote(note: Note) {
        binding.editTitle.setText(note.title)
        binding.editCourse.setText(note.course)
        binding.editContent.setText(note.content)
        val chipId = when (NoteCategory.fromKey(note.category)) {
            NoteCategory.LECTURE -> R.id.chip_cat_lecture
            NoteCategory.ASSIGNMENT -> R.id.chip_cat_assignment
            NoteCategory.EXAM -> R.id.chip_cat_exam
            NoteCategory.URGENT -> R.id.chip_cat_urgent
            null -> null
        }
        if (chipId != null) binding.chipGroupCategory.check(chipId) else binding.chipGroupCategory.clearCheck()
    }

    private fun saveAndClose() {
        viewModel.saveAndClose(
            title = binding.editTitle.text.toString(),
            course = binding.editCourse.text.toString(),
            category = selectedCategory(),
            content = content()
        )
    }

    private fun openForText(screen: Class<*>) {
        addText.launch(Intent(this, screen), ActivityOptionsCompat.makeSceneTransitionAnimation(this))
    }

    private fun startVoiceDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.dictate_prompt))
        try {
            dictateText.launch(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.error_speech_unavailable)
        }
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = runOnUiThread { setSpeakingState(true) }
                override fun onDone(utteranceId: String?) = runOnUiThread { setSpeakingState(false) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = runOnUiThread { setSpeakingState(false) }
            })
        }
    }

    private fun toggleReadAloud() {
        val engine = tts
        if (speaking) {
            engine?.stop()
            setSpeakingState(false)
            return
        }
        val textToRead = buildString {
            val title = binding.editTitle.text.toString().trim()
            if (title.isNotEmpty()) appendLine(title)
            val body = content().trim()
            if (body.isNotEmpty()) append(body)
        }.trim()
        if (textToRead.isEmpty()) {
            shakeIfEmpty()
            showMessage(R.string.error_note_empty)
            return
        }
        if (engine == null || !ttsReady) {
            showMessage(R.string.error_tts_unavailable)
            return
        }
        setSpeakingState(true)
        engine.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun setSpeakingState(active: Boolean) {
        speaking = active
        binding.chipSpeak.setText(if (active) R.string.tool_stop_speaking else R.string.tool_speak)
    }

    private fun shareNote() {
        val title = binding.editTitle.text.toString()
        val body = content()
        if (title.isBlank() && body.isBlank()) {
            shakeIfEmpty()
            showMessage(R.string.error_note_empty)
            return
        }
        val shared = NoteExporter.formatForShare(
            context = this,
            title = title,
            course = binding.editCourse.text.toString(),
            category = selectedCategory(),
            content = body,
            summary = viewModel.summary.value.orEmpty()
        )
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { getString(R.string.untitled_note) })
            .putExtra(Intent.EXTRA_TEXT, shared)
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }

    private fun promptExportPdf() {
        val title = binding.editTitle.text.toString().trim()
        val body = content()
        if (title.isEmpty() && body.isBlank()) {
            shakeIfEmpty()
            showMessage(R.string.error_note_empty)
            return
        }
        val safeName = title.ifEmpty { getString(R.string.untitled_note) }
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .trim()
        createPdf.launch("$safeName.pdf")
    }

    private fun appendToNote(text: String) {
        val current = content()
        binding.editContent.setText(if (current.isBlank()) text else "${current.trimEnd()}\n$text")
        binding.editContent.setSelection(binding.editContent.length())
    }

    /** Tween animation: the note field shakes when a tool is tapped while the note is empty. */
    private fun shakeIfEmpty() {
        if (content().isBlank()) binding.layoutContent.play(R.anim.shake)
    }

    /** Shows or hides a result card. Tween animation: it fades in when it appears. */
    private fun MaterialCardView.show(visible: Boolean) {
        if (visible && !isVisible) play(R.anim.fade_in)
        isVisible = visible
    }

    private fun updateWordCount() {
        val words = TextHelpers.wordCount(content())
        binding.textWordCount.text = if (words == 0) {
            getString(R.string.word_count_empty)
        } else {
            getString(R.string.word_count_stats, words, TextHelpers.readingMinutes(words))
        }
    }

    private fun openFlashcards() {
        val cards = TextHelpers.extractFlashcards(
            title = binding.editTitle.text.toString(),
            content = content(),
            summary = viewModel.summary.value.orEmpty()
        )
        if (cards.isEmpty()) {
            shakeIfEmpty()
            showMessage(R.string.no_flashcards_found)
            return
        }
        showFlashcardAt(cards, index = 0, flipped = false)
    }

    private fun showFlashcardAt(cards: List<Flashcard>, index: Int, flipped: Boolean) {
        val card = cards[index]
        val body = if (flipped) {
            "${getString(R.string.flashcard_back_label)}\n\n${card.back}"
        } else {
            "${getString(R.string.flashcard_front_label)}\n\n${card.front}"
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.flashcards_title, index + 1, cards.size))
            .setMessage(body)
            .setNeutralButton(R.string.flashcard_flip) { _, _ ->
                showFlashcardAt(cards, index, !flipped)
            }
            .setNegativeButton(R.string.flashcard_close, null)
        if (cards.size > 1) {
            builder.setPositiveButton(R.string.flashcard_next) { _, _ ->
                showFlashcardAt(cards, (index + 1) % cards.size, flipped = false)
            }
        }
        builder.show()
    }

    /** The dates and smart entities found in the note; tapping one opens the calendar, email, dialer, browser or maps. */
    private fun showDates(found: Pair<List<Deadline>, List<SmartEntity>>) {
        val (dates, entities) = found
        val dateLabels = dates.map { "${it.formatWhen()}\n${it.line}" }
        val entityLabels = entities.map { entity ->
            when (entity.type) {
                SmartEntity.Type.EMAIL -> getString(R.string.smart_action_email, entity.text)
                SmartEntity.Type.PHONE -> getString(R.string.smart_action_phone, entity.text)
                SmartEntity.Type.URL -> getString(R.string.smart_action_url, entity.text)
                SmartEntity.Type.ADDRESS -> getString(R.string.smart_action_address, entity.text)
            }
        }
        val labels = (dateLabels + entityLabels).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dates_dialog_title)
            .setItems(labels) { _, which ->
                if (which < dates.size) addToCalendar(dates[which]) else openSmartEntity(entities[which - dates.size])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        /** Long: the id of the note to edit. Missing or 0 starts a new note. */
        const val EXTRA_NOTE_ID = "com.group15.notemate.extra.NOTE_ID"

        private const val UTTERANCE_ID = "notemate_read_aloud"
        private const val ALPHA_PINNED = 255
        private const val ALPHA_UNPINNED = 90
    }
}
