package com.group15.notemate.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.group15.notemate.R
import com.group15.notemate.databinding.ActivityHandwritingBinding
import com.group15.notemate.viewmodel.HandwritingViewModel

/**
 * MVVM: View. Write on the screen, convert each line to text with Digital Ink Recognition,
 * correct it and send it back to the note editor.
 */
class HandwritingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHandwritingBinding
    private val viewModel: HandwritingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        slideInFromBottom()
        binding = ActivityHandwritingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        observeMessages(viewModel)

        binding.drawing.onStrokeFinished = viewModel::addStroke
        binding.buttonClear.setOnClickListener { viewModel.clear() }
        binding.buttonConvert.setOnClickListener { viewModel.recognize() }
        binding.editText.doAfterTextChanged { binding.buttonAdd.isEnabled = !it.isNullOrBlank() }
        binding.buttonAdd.setOnClickListener { returnText(binding.editText.text.toString().trim()) }

        viewModel.strokes.observe(this, binding.drawing::setStrokes)
        viewModel.isLoading.observe(this) { loading ->
            binding.progress.isVisible = loading
            binding.buttonConvert.isEnabled = !loading
        }
        viewModel.recognized.observe(this) { event -> event.getContentIfNotHandled()?.let(::addLine) }
    }

    /** Each converted line goes on a new line of the text field. */
    private fun addLine(line: String) {
        val current = binding.editText.text.toString()
        binding.editText.setText(if (current.isBlank()) line else "${current.trimEnd()}\n$line")
        // Tween animation: the button pops to show the text is ready to be added
        binding.buttonAdd.play(R.anim.pop)
    }

    override fun onSupportNavigateUp(): Boolean {
        finishAfterTransition()
        return true
    }
}
