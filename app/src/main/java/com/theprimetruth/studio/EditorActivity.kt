package com.theprimetruth.studio

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream

@UnstableApi
class EditorActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var textureView: TextureView
    private lateinit var statusText: TextView
    private lateinit var timelineStrip: LinearLayout
    private lateinit var timelineScroll: HorizontalScrollView
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText
    private lateinit var headlineInput: EditText
    private lateinit var exportButton: Button
    private lateinit var voiceButton: Button

    private val editEngine = VideoEditEngine()
    private var selectedClipId: Long? = null
    private var musicUri: Uri? = null
    private var voiceOverUri: Uri? = null
    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var exporting = false

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) { }
                    editEngine.addClip(uri)
                }
                selectedClipId = editEngine.getClips().lastOrNull()?.id
                refreshTimeline()
                selectedClip()?.let { previewClip(it) }
                statusText.text = "${uris.size} video clip(s) added to timeline"
            }
        }

    private val musicPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                musicUri = uri
                statusText.text = "Background music selected"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        buildInterface()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 12)
            setBackgroundColor(Color.rgb(9, 13, 18))
        }

        fun label(text: String, size: Float = 14f, color: Int = Color.WHITE) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(4, 8, 4, 8)
            })
        }

        fun button(text: String, action: () -> Unit): Button {
            val b = Button(this).apply {
                this.text = text
                isAllCaps = false
                setOnClickListener { action() }
            }
            root.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 3; bottomMargin = 3 })
            return b
        }

        label("THE PRIME TRUTH", 23f, Color.rgb(229, 193, 43))
        label("EDITING STUDIO • MULTI-CLIP TIMELINE", 15f)

        val preview = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        textureView = TextureView(this)
        preview.addView(textureView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 220
        ))
        player.setVideoTextureView(textureView)

        val playerControls = androidx.media3.ui.PlayerControlView(this).apply {
            player = this@EditorActivity.player
            setShowTimeoutMs(0)
        }
        root.addView(playerControls)

        statusText = TextView(this).apply {
            text = "Import one or more videos to begin"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(5, 8, 5, 8)
        }
        root.addView(statusText)

        button("＋ IMPORT VIDEO CLIPS") {
            videoPicker.launch(arrayOf("video/*"))
        }

        label("TIMELINE — tap a clip to select it", 14f, Color.rgb(229, 193, 43))
        timelineScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            setBackgroundColor(Color.rgb(24, 31, 40))
        }
        timelineStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(6, 6, 6, 6)
        }
        timelineScroll.addView(timelineStrip)
        root.addView(timelineScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 74
        ))

        val trimTitle = TextView(this).apply {
            text = "TRIM SELECTED CLIP (SECONDS)"
            setTextColor(Color.rgb(229, 193, 43))
            gravity = Gravity.CENTER
            setPadding(4, 10, 4, 4)
        }
        root.addView(trimTitle)

        val trimRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startInput = EditText(this).apply {
            hint = "Start seconds"
            inputType = 8194
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
        }
        endInput = EditText(this).apply {
            hint = "End seconds"
            inputType = 8194
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
        }
        trimRow.addView(startInput, LinearLayout.LayoutParams(0, 52, 1f))
        trimRow.addView(endInput, LinearLayout.LayoutParams(0, 52, 1f))
        root.addView(trimRow)

        val editActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        fun smallAction(text: String, action: () -> Unit) {
            editActions.addView(Button(this).apply {
                this.text = text
                isAllCaps = false
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, 52, 1f))
        }
        smallAction("APPLY TRIM") { applyTrim() }
        smallAction("SPLIT AT PLAYHEAD") { splitAtPlayhead() }
        root.addView(editActions)

        val clipActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        fun clipAction(text: String, action: () -> Unit) {
            clipActions.addView(Button(this).apply {
                this.text = text
                textSize = 11f
                isAllCaps = false
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, 48, 1f))
        }
        clipAction("REMOVE") { removeSelectedClip() }
        clipAction("MOVE ◀") { moveSelected(-1) }
        clipAction("MOVE ▶") { moveSelected(1) }
        root.addView(clipActions)

        label("AUDIO STUDIO", 14f, Color.rgb(229, 193, 43))
        button("＋ ADD BACKGROUND MUSIC") {
            musicPicker.launch("audio/*")
        }
        voiceButton = button("● RECORD VOICE-OVER") {
            toggleVoiceOver()
        }
        button("REMOVE MUSIC / VOICE-OVER") {
            musicUri = null
            if (recorder != null) stopVoiceOver()
            voiceOverUri = null
            statusText.text = "Additional audio tracks cleared"
        }

        label("NEWS BRANDING", 14f, Color.rgb(229, 193, 43))
        headlineInput = EditText(this).apply {
            hint = "Headline / lower-third (Assamese, Hindi or English)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
        }
        root.addView(headlineInput)

        exportButton = button("EXPORT BRANDED MP4") { exportTimeline() }

        button("BACK TO RECORDING STUDIO") { finish() }

        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            addView(root)
            isFillViewport = true
        }
        setContentView(scroll)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) statusText.text = "Loading preview…"
                if (playbackState == Player.STATE_READY) statusText.text = "Preview ready"
                if (playbackState == Player.STATE_ENDED) statusText.text = "Preview finished"
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                statusText.text = "Preview error: ${error.message}"
            }
        })
    }

    private fun selectedClip(): VideoClip? {
        val id = selectedClipId ?: return null
        return editEngine.getClips().firstOrNull { it.id == id }
    }

    private fun previewClip(clip: VideoClip) {
        player.setMediaItem(MediaItem.fromUri(clip.uri))
        player.prepare()
        player.seekTo(clip.startMs)
        startInput.setText((clip.startMs / 1000.0).toString())
        endInput.setText(clip.endMs?.let { (it / 1000.0).toString() } ?: "")
    }

    private fun refreshTimeline() {
        timelineStrip.removeAllViews()
        val clips = editEngine.getClips()
        clips.forEachIndexed { index, clip ->
            val selected = clip.id == selectedClipId
            val tile = TextView(this).apply {
                text = "CLIP ${index + 1}\n${clip.startMs / 1000}s – ${clip.endMs?.div(1000) ?: "END"}"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(12, 6, 12, 6)
                setBackgroundColor(
                    if (selected) Color.rgb(130, 91, 0) else Color.rgb(48, 59, 72)
                )
                setOnClickListener {
                    selectedClipId = clip.id
                    refreshTimeline()
                    previewClip(clip)
                    statusText.text = "Selected clip ${index + 1}"
                }
            }
            timelineStrip.addView(tile, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = 7 })
        }
        if (clips.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No clips yet"
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER_VERTICAL
            }
            timelineStrip.addView(empty)
        }
    }

    private fun applyTrim() {
        val clip = selectedClip()
        if (clip == null) {
            toast("Import and select a clip first.")
            return
        }
        val start = (startInput.text.toString().toDoubleOrNull() ?: 0.0)
        val end = endInput.text.toString().toDoubleOrNull()
        if (start < 0 || (end != null && end <= start)) {
            toast("End time must be greater than start time.")
            return
        }
        val endMs = end?.let { (it * 1000).toLong() }
        if (!editEngine.trimClip(clip.id, (start * 1000).toLong(), endMs ?: player.duration)) {
            toast("Could not apply trim. Check the clip duration.")
            return
        }
        refreshTimeline()
        selectedClip()?.let { previewClip(it) }
        statusText.text = "Trim applied to selected clip"
    }

    private fun splitAtPlayhead() {
        val clip = selectedClip()
        if (clip == null) {
            toast("Select a clip first.")
            return
        }
        val sourceDuration = player.duration
        if (sourceDuration <= 0L) {
            toast("Wait for the clip preview to load.")
            return
        }
        val splitPosition = player.currentPosition
        if (!editEngine.splitClip(clip.id, splitPosition, sourceDuration)) {
            toast("Move the playhead inside the selected clip before splitting.")
            return
        }
        selectedClipId = editEngine.getClips().firstOrNull { it.id == clip.id }?.id
        refreshTimeline()
        selectedClip()?.let { previewClip(it) }
        statusText.text = "Clip split at ${splitPosition / 1000.0} seconds"
    }

    private fun removeSelectedClip() {
        val id = selectedClipId ?: return toast("Select a clip first.")
        editEngine.removeClip(id)
        selectedClipId = editEngine.getClips().firstOrNull()?.id
        refreshTimeline()
        selectedClip()?.let { previewClip(it) } ?: player.clearMediaItems()
        statusText.text = "Selected clip removed"
    }

    private fun moveSelected(direction: Int) {
        val id = selectedClipId ?: return toast("Select a clip first.")
        val clips = editEngine.getClips()
        val index = clips.indexOfFirst { it.id == id }
        val target = index + direction
        if (index < 0 || target !in clips.indices || !editEngine.moveClip(index, target)) {
            toast("Clip cannot move further in that direction.")
            return
        }
        refreshTimeline()
        statusText.text = "Timeline order updated"
    }

    @Suppress("DEPRECATION")
    private fun toggleVoiceOver() {
        if (recorder != null) {
            stopVoiceOver()
            return
        }
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
            voiceFile = File(dir, "TPT_VOICE_${System.currentTimeMillis()}.m4a")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(voiceFile!!.absolutePath)
                prepare()
                start()
            }
            voiceButton.text = "■ STOP VOICE-OVER"
            statusText.text = "Recording voice-over…"
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            toast("Voice-over could not start: ${e.message}")
        }
    }

    private fun stopVoiceOver() {
        try {
            recorder?.stop()
            voiceOverUri = voiceFile?.let { Uri.fromFile(it) }
            statusText.text = "Voice-over recorded and added to export"
        } catch (e: Exception) {
            toast("Voice-over recording was too short or failed.")
        } finally {
            recorder?.release()
            recorder = null
            voiceButton.text = "● RECORD VOICE-OVER"
        }
    }

    private fun exportTimeline() {
        if (exporting) return
        val clips = editEngine.getClips()
        if (clips.isEmpty()) {
            toast("Import at least one video clip first.")
            return
        }
        exporting = true
        exportButton.isEnabled = false
        statusText.text = "Preparing branded timeline export…"

        try {
            val logoBitmap = android.graphics.BitmapFactory.decodeResource(
                resources, R.drawable.prime_truth_square
            )
            val headline = headlineInput.text.toString().trim()
            val videoItems = clips.map { clip ->
                val overlays = mutableListOf<androidx.media3.effect.TextureOverlay>()
                val logoSettings = StaticOverlaySettings.Builder()
                    .setOverlayFrameAnchor(0.88f, 0.86f)
                    .setBackgroundFrameAnchor(0.88f, 0.86f)
                    .setScale(0.18f, 0.18f)
                    .setAlphaScale(0.94f)
                    .build()
                overlays.add(BitmapOverlay.createStaticBitmapOverlay(logoBitmap, logoSettings))

                if (headline.isNotEmpty()) {
                    val titleBitmap = createHeadlineBitmap(headline)
                    val titleSettings = StaticOverlaySettings.Builder()
                        .setOverlayFrameAnchor(0f, -0.72f)
                        .setBackgroundFrameAnchor(0f, -0.72f)
                        .setScale(1f, 1f)
                        .build()
                    overlays.add(BitmapOverlay.createStaticBitmapOverlay(titleBitmap, titleSettings))
                }
                val overlayEffect = OverlayEffect(overlays)
                EditedMediaItem.Builder(clip.toMediaItem())
                    .setEffects(Effects(emptyList(), listOf(overlayEffect)))
                    .build()
            }

            val sequences = mutableListOf<EditedMediaItemSequence>()
            sequences.add(
                EditedMediaItemSequence.withAudioAndVideoFrom(videoItems)
            )

            musicUri?.let { uri ->
                val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
                sequences.add(
                    EditedMediaItemSequence.withAudioFrom(listOf(musicItem))
                        .buildUpon()
                        .setIsLooping(true)
                        .build()
                )
            }

            voiceOverUri?.let { uri ->
                val voiceItem = EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
                sequences.add(EditedMediaItemSequence.withAudioFrom(listOf(voiceItem)))
            }

            val composition = Composition.Builder(sequences).build()
            val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            val output = File(outputDir, "TPT_EDITED_${System.currentTimeMillis()}.mp4")

            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        exporting = false
                        exportButton.isEnabled = true
                        saveToGallery(output)
                        statusText.text = "Export complete • Saved to Movies/ThePrimeTruthStudio"
                        toast("Your edited MP4 has been saved to the gallery.")
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        exporting = false
                        exportButton.isEnabled = true
                        statusText.text = "Export failed: ${exportException.message}"
                        toast("Export failed. Try compatible MP4 clips/audio.")
                    }
                })
                .build()
            transformer.start(composition, output.absolutePath)
        } catch (e: Exception) {
            exporting = false
            exportButton.isEnabled = true
            statusText.text = "Could not start export: ${e.message}"
            toast("Export could not start: ${e.message}")
        }
    }

    private fun createHeadlineBitmap(headline: String): Bitmap {
        val bitmap = Bitmap.createBitmap(1280, 210, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 12, 12, 12)
        }
        canvas.drawRect(0f, 35f, 1280f, 185f, background)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(218, 25, 25) }
        canvas.drawRect(0f, 35f, 14f, 185f, accent)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 55f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val safe = headline.take(90)
        val maxWidth = 1190f
        val measured = textPaint.measureText(safe)
        if (measured > maxWidth) textPaint.textSize *= maxWidth / measured
        canvas.drawText(safe, 42f, 128f, textPaint)
        return bitmap
    }

    private fun saveToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ThePrimeTruthStudio")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            }
            val done = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            contentResolver.update(uri, done, null, null)
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        if (recorder != null) {
            try { recorder?.stop() } catch (_: Exception) { }
            recorder?.release()
            recorder = null
        }
        player.release()
        super.onDestroy()
    }
}
