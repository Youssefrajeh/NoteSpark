package com.group15.notemate.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.group15.notemate.R
import com.group15.notemate.databinding.ActivityScanBinding
import com.group15.notemate.viewmodel.ScanViewModel

/**
 * MVVM: View. Scan a page with the ML Kit Document Scanner (or pick an image), read its text with
 * Text Recognition, correct it and send it back to the note editor.
 */
class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private val viewModel: ScanViewModel by viewModels()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::readImage)
    }

    /** ML Kit Document Scanner: Google Play services shows its own scanning screen and returns cropped page images. */
    private val scanDocument = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uris = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            ?.pages?.map { it.imageUri }.orEmpty()
        if (uris.isNotEmpty()) viewModel.readImages(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        slideInFromBottom()
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        observeMessages(viewModel)

        binding.buttonScanDocument.setOnClickListener { startDocumentScanner() }
        binding.buttonPickImage.setOnClickListener { pickImage.launch("image/*") }
        binding.editText.doAfterTextChanged { binding.buttonAdd.isEnabled = !it.isNullOrBlank() }
        binding.buttonAdd.setOnClickListener { returnText(binding.editText.text.toString().trim()) }

        viewModel.imageUri.observe(this) { uri ->
            binding.imagePreview.setImageURI(uri)
            // Tween animation: the scanned page fades in
            if (uri != null && !binding.cardPreview.isVisible) binding.cardPreview.play(R.anim.fade_in)
            binding.cardPreview.isVisible = uri != null
        }
        viewModel.isLoading.observe(this) { loading ->
            binding.progress.isVisible = loading
            binding.buttonScanDocument.isEnabled = !loading
            binding.buttonPickImage.isEnabled = !loading
        }
        viewModel.recognized.observe(this) { event ->
            event.getContentIfNotHandled()?.let { text ->
                binding.editText.setText(text)
                // Tween animation: the button pops to show the text is ready to be added
                binding.buttonAdd.play(R.anim.pop)
            }
        }
    }

    private fun startDocumentScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(MAX_SCAN_PAGES)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { intentSender -> scanDocument.launch(IntentSenderRequest.Builder(intentSender).build()) }
            .addOnFailureListener { showMessage(R.string.error_scanner_unavailable) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finishAfterTransition()
        return true
    }

    private companion object {
        const val MAX_SCAN_PAGES = 5
    }
}
