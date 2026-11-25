package com.theprimetruth.studio

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.video.AudioConfig
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.theprimetruth.studio.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.Executor

@UnstableApi
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: LifecycleCameraController
    private var recording: androidx.camera.video.Recording? = null
    private var selectedVideo: Uri? = null
    private val cameraExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    private val videoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideo = uri
            binding.statusText.text = "Video selected. Export to apply your logo."
            binding.previewLogo.visibility = android.view.View.VISIBLE
        }
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.CAMERA] == true) startCamera()
        else toast("Camera permission is required to record.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.previewLogo.visibility = android.view.View.VISIBLE

        binding.importButton.setOnClickListener { videoPicker.launch("video/*") }
        binding.recordButton.setOnClickListener { toggleRecording() }
        binding.exportButton.setOnClickListener { exportWithWatermark() }
        binding.editVideoButton.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }

        val required = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (required.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else permissionRequest.launch(required)
    }

    private fun startCamera() {
        cameraController = LifecycleCameraController(this).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
            setVideoCaptureQualitySelector(QualitySelector.from(Quality.FHD))
            bindToLifecycle(this@MainActivity)
        }
        binding.cameraPreview.controller = cameraController
        binding.statusText.text = "Camera ready • 1080p where supported"
    }

    private fun toggleRecording() {
        if (!::cameraController.isInitialized) {
            toast("Camera is not ready.")
            return
        }
        if (recording != null) {
            recording?.stop()
            recording = null
            binding.recordButton.text = "● Record"
            return
        }
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val outputFile = File(outputDir, "TPT_${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(outputFile).build()
        recording = cameraController.startRecording(options, AudioConfig.create(true), cameraExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    binding.recordButton.text = "■ Stop"
                    binding.statusText.text = "Recording…"
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    binding.recordButton.text = "● Record"
                    if (!event.hasError()) {
                        selectedVideo = Uri.fromFile(outputFile)
                        binding.statusText.text = "Recording saved • Ready to export with logo"
                    } else binding.statusText.text = "Recording error: ${event.error}"
                }
            }
        }
    }

    private fun exportWithWatermark() {
        val input = selectedVideo
        if (input == null) {
            toast("Record a video or import one first.")
            return
        }
        binding.exportButton.isEnabled = false
        binding.statusText.text = "Applying permanent logo watermark…"
        try {
            val logo = BitmapFactory.decodeResource(resources, R.drawable.prime_truth_square)
            val settings = StaticOverlaySettings.Builder()
                .setOverlayFrameAnchor(0.88f, 0.86f)
                .setBackgroundFrameAnchor(0.88f, 0.86f)
                .setScale(0.20f, 0.20f)
                .setAlphaScale(0.94f)
                .build()
            val overlay = BitmapOverlay.createStaticBitmapOverlay(logo, settings)
            val overlayEffect = OverlayEffect(listOf(overlay))
            val overlays = mutableListOf<androidx.media3.effect.TextureOverlay>()
            overlays.add(overlay)
            val headline = binding.headlineInput.text?.toString()?.trim().orEmpty()
            if (headline.isNotEmpty()) {
                val headlineBitmap = createHeadlineBitmap(headline)
                val headlineSettings = StaticOverlaySettings.Builder()
                    .setOverlayFrameAnchor(0f, -0.72f)
                    .setBackgroundFrameAnchor(0f, -0.72f)
                    .setScale(1f, 1f)
                    .build()
                overlays.add(BitmapOverlay.createStaticBitmapOverlay(headlineBitmap, headlineSettings))
            }
            val combinedOverlayEffect = OverlayEffect(overlays)
            val startMs = binding.trimStartInput.text?.toString()?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 0L
            val endMs = binding.trimEndInput.text?.toString()?.toDoubleOrNull()?.let { (it * 1000).toLong() }
            if (startMs < 0L || (endMs != null && endMs <= startMs)) {
                binding.exportButton.isEnabled = true
                toast("Check trim values: end time must be greater than start time.")
                return
            }
            val mediaItemBuilder = MediaItem.Builder().setUri(input)
            if (startMs > 0L || endMs != null) {
                val clipping = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .apply { if (endMs != null) setEndPositionMs(endMs) }
                    .build()
                mediaItemBuilder.setClippingConfiguration(clipping)
            }
            val edited = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setEffects(Effects(emptyList(), listOf(combinedOverlayEffect)))
                .build()
            val output = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "TPT_BRANDED_${System.currentTimeMillis()}.mp4")
            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                        binding.exportButton.isEnabled = true
                        binding.statusText.text = "Branded MP4 saved: ${output.name}"
                        saveToGallery(output)
                        toast("Export complete. Saved to Movies.")
                    }
                    override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                        binding.exportButton.isEnabled = true
                        binding.statusText.text = "Export failed: ${exportException.message}"
                        toast("Export failed. Try another source video.")
                    }
                })
                .build()
            transformer.start(edited, output.absolutePath)
        } catch (e: Exception) {
            binding.exportButton.isEnabled = true
            binding.statusText.text = "Could not start export: ${e.message}"
        }
    }

    private fun createHeadlineBitmap(headline: String): Bitmap {
        val width = 1280
        val height = 210
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 12, 12, 12) }
        canvas.drawRect(0f, 35f, width.toFloat(), 185f, background)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(218, 25, 25) }
        canvas.drawRect(0f, 35f, 14f, 185f, accent)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize =  fiftyFive()
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        val safeText = headline.take(90)
        val availableWidth = width - 90f
        val measured = textPaint.measureText(safeText)
        if (measured > availableWidth) textPaint.textSize *= availableWidth / measured
        canvas.drawText(safeText, 42f, 128f, textPaint)
        return bitmap
    }

    private fun fiftyFive(): Float = 55f

    private fun saveToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ThePrimeTruthStudio")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
        contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
