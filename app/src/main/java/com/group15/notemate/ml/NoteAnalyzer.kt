package com.group15.notemate.ml

import android.content.Context
import android.net.Uri
import androidx.concurrent.futures.await
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.group15.notemate.R
import com.group15.notemate.model.Deadline
import com.group15.notemate.model.Note
import com.group15.notemate.model.RecognizedText
import com.group15.notemate.model.SmartEntity
import com.group15.notemate.model.StrokePoint
import com.group15.notemate.model.TranslatedText
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * MVVM: Model. All ML Kit calls. Every function is a suspend function that awaits the ML Kit Task
 * (or, for Gemini Nano, the ListenableFuture).
 */
class NoteAnalyzer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Text Recognition v2: reads the text in a scanned page or a picture.
     *
     * The recognizer always returns its best guess, even for marks it cannot really read (handwriting,
     * smudges, a picture, or a script other than Latin). Those guesses look like random letters, so:
     * - lines the recognizer is not confident about are dropped;
     * - Language ID checks what is left: when it cannot tell the language, the text is marked as not readable
     *   so the screen can warn the student to check it.
     */
    suspend fun readTextFromImage(uri: Uri): RecognizedText {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = try {
            // fromFilePath reads the EXIF orientation, so a sideways photo is turned upright first
            recognizer.process(InputImage.fromFilePath(appContext, uri)).await()
        } finally {
            recognizer.close()
        }
        // One block is one paragraph; its lines keep their reading order
        val text = result.textBlocks
            .map { block -> block.lines.filter { it.confidence >= MIN_LINE_CONFIDENCE }.joinToString("\n") { it.text } }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        if (text.isBlank()) {
            throw AnalyzerException(if (result.text.isBlank()) R.string.error_no_text_found else R.string.error_text_unclear)
        }
        return RecognizedText(text, readable = detectLanguage(text) != UNDETERMINED_LANGUAGE)
    }

    /** Reads one or more scanned pages in order and joins their text. */
    suspend fun readTextFromImages(uris: List<Uri>): RecognizedText {
        val pages = uris.map { readTextFromImage(it) }
        return RecognizedText(
            text = pages.joinToString("\n\n") { it.text },
            readable = pages.all { it.readable }
        )
    }

    /**
     * Digital Ink Recognition: turns the strokes drawn on the screen into text (one line of English).
     * The model is downloaded the first time.
     */
    suspend fun recognizeHandwriting(strokes: List<List<StrokePoint>>): String {
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(HANDWRITING_LANGUAGE)
            ?: throw AnalyzerException(R.string.error_handwriting_unavailable)
        val model = DigitalInkRecognitionModel.builder(identifier).build()
        RemoteModelManager.getInstance().download(model, DownloadConditions.Builder().build()).await()

        val ink = Ink.builder()
        for (stroke in strokes) {
            val builder = Ink.Stroke.builder()
            for (point in stroke) builder.addPoint(Ink.Point.create(point.x, point.y, point.time))
            ink.addStroke(builder.build())
        }
        val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        try {
            return recognizer.recognize(ink.build()).await().candidates.firstOrNull()?.text.orEmpty()
        } finally {
            recognizer.close()
        }
    }

    /**
     * Entity Extraction: every date and time in the title and text of [notes], earliest first.
     * The model is downloaded the first time.
     */
    suspend fun findDeadlines(notes: List<Note>): List<Deadline> {
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
        try {
            extractor.downloadModelIfNeeded().await()
            val found = mutableListOf<Deadline>()
            for (note in notes) {
                val text = "${note.title}\n${note.content}"
                if (text.isBlank()) continue
                val annotations = extractor.annotate(EntityExtractionParams.Builder(text).build()).await()
                for (annotation in annotations) {
                    val date = annotation.entities.firstNotNullOfOrNull { it.asDateTimeEntity() } ?: continue
                    found += Deadline(
                        noteId = note.id,
                        noteTitle = note.displayTitle,
                        line = TextHelpers.sentenceAround(text, annotation.start, annotation.end),
                        millis = date.timestampMillis,
                        hasTime = date.dateTimeGranularity in TIME_GRANULARITIES
                    )
                }
            }
            return found.distinct().sortedBy { it.millis }
        } finally {
            extractor.close()
        }
    }

    /** Entity Extraction: emails, phone numbers, URLs and physical addresses found in [text]. */
    suspend fun findSmartEntities(text: String): List<SmartEntity> {
        if (text.isBlank()) return emptyList()
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
        try {
            extractor.downloadModelIfNeeded().await()
            val annotations = extractor.annotate(EntityExtractionParams.Builder(text).build()).await()
            val found = mutableListOf<SmartEntity>()
            for (annotation in annotations) {
                for (entity in annotation.entities) {
                    val type = when (entity.type) {
                        Entity.TYPE_EMAIL -> SmartEntity.Type.EMAIL
                        Entity.TYPE_PHONE -> SmartEntity.Type.PHONE
                        Entity.TYPE_URL -> SmartEntity.Type.URL
                        Entity.TYPE_ADDRESS -> SmartEntity.Type.ADDRESS
                        else -> null
                    }
                    if (type != null) {
                        found += SmartEntity(type, annotation.annotatedText.trim())
                    }
                }
            }
            return found.distinct()
        } finally {
            extractor.close()
        }
    }

    /** Language ID: returns a BCP-47 tag such as "en" or "fr", or "und" when unsure. */
    private suspend fun detectLanguage(text: String): String {
        val identifier = LanguageIdentification.getClient()
        try {
            return identifier.identifyLanguage(text).await()
        } finally {
            identifier.close()
        }
    }

    /** Language ID + on-device Translation of any supported language to English. */
    suspend fun translateToEnglish(text: String): TranslatedText {
        val tag = detectLanguage(text)
        if (tag == UNDETERMINED_LANGUAGE) throw AnalyzerException(R.string.error_language_unknown)
        if (tag == "en") throw AnalyzerException(R.string.error_already_english)
        val source = TranslateLanguage.fromLanguageTag(tag)
            ?: throw AnalyzerException(R.string.error_language_unsupported)
        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            // Line by line, so the note keeps its lines
            val translated = text.lines()
                .map { line -> if (line.isBlank()) line else translator.translate(line).await() }
                .joinToString("\n")
            val languageName = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault())
            return TranslatedText(languageName, translated)
        } finally {
            translator.close()
        }
    }

    /**
     * Gemini Nano (ML Kit GenAI Summarization): a three-bullet summary of the note, made on the device.
     * Only phones with Gemini Nano (AICore) support it; the model is downloaded the first time.
     */
    suspend fun summarize(text: String): String {
        val summarizer = Summarization.getClient(
            SummarizerOptions.builder(appContext)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
                .setLanguage(SummarizerOptions.Language.ENGLISH)
                .setLongInputAutoTruncationEnabled(true)
                .build()
        )
        try {
            when (summarizer.checkFeatureStatus().await()) {
                FeatureStatus.UNAVAILABLE -> throw AnalyzerException(R.string.error_summary_unavailable)
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                    summarizer.downloadFeature(NoProgressCallback).await()
            }
            val request = SummarizationRequest.builder(text).build()
            return summarizer.runInference(request).await().summary.trim()
        } catch (e: GenAiException) {
            throw AnalyzerException(R.string.error_summary_failed, e)
        } finally {
            summarizer.close()
        }
    }

    /** The download progress is not shown; the progress bar spins while summarize() runs. */
    private object NoProgressCallback : DownloadCallback {
        override fun onDownloadStarted(bytesToDownload: Long) = Unit
        override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit
        override fun onDownloadCompleted() = Unit
        override fun onDownloadFailed(e: GenAiException) = Unit
    }

    private companion object {
        const val HANDWRITING_LANGUAGE = "en-US"

        /** What Language ID returns when it cannot tell the language. */
        const val UNDETERMINED_LANGUAGE = "und"

        /** Lines Text Recognition is less sure about than this (0 to 1) are usually noise, not words. */
        const val MIN_LINE_CONFIDENCE = 0.5f

        /** Date-time entities this precise include a time of day, not only a day. */
        val TIME_GRANULARITIES = setOf(
            DateTimeEntity.GRANULARITY_HOUR,
            DateTimeEntity.GRANULARITY_MINUTE,
            DateTimeEntity.GRANULARITY_SECOND
        )
    }
}
