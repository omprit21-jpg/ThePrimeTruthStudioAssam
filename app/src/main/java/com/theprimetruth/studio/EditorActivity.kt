
package com.theprimetruth.studio

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView

class EditorActivity : AppCompatActivity() {

    private lateinit var videoTexture: TextureView
    private lateinit var statusText: TextView
    private lateinit var player: ExoPlayer
    private lateinit var controls: PlayerControlView

    private var selectedVideo: Uri? = null

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedVideo = uri
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = false
                statusText.text = "Video loaded • Press PLAY to watch"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.rgb(9, 13, 18))
        }

        fun addText(text: String, size: Float, color: Int) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(6, 10, 6, 10)
            })
        }

        fun addButton(text: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            })
        }

        addText("THE PRIME TRUTH", 24f, Color.rgb(229, 193, 43))
        addText("EDITING STUDIO", 19f, Color.WHITE)

        val previewFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        videoTexture = TextureView(this)

        previewFrame.addView(
            videoTexture,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            previewFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                230
            )
        )

        player.setVideoTextureView(videoTexture)

        controls = PlayerControlView(this).apply {
            player = this@EditorActivity.player
            setShowTimeoutMs(0)
        }

        root.addView(
            controls,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        statusText = TextView(this).apply {
            text = "Import a video to begin editing"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
        }
        root.addView(statusText)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING ->
                        statusText.text = "Loading video..."

                    Player.STATE_READY ->
                        statusText.text = "Video ready"

                    Player.STATE_ENDED ->
                        statusText.text = "Playback completed"
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                statusText.text = "Playback error: ${error.message}"
            }
        })

        addButton("IMPORT VIDEO") {
            videoPicker.launch("video/*")
        }

        addButton("PLAY / PAUSE") {
            if (selectedVideo == null) {
                statusText.text = "Please import a video first"
            } else if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }

        addText("TRIM VIDEO (SECONDS)", 14f, Color.rgb(229, 193, 43))

        val trimRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val startInput = EditText(this).apply {
            hint = "Start seconds"
            inputType = 8194
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }

        val endInput = EditText(this).apply {
            hint = "End seconds"
            inputType = 8194
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }

        trimRow.addView(startInput, LinearLayout.LayoutParams(0, 55, 1f))
        trimRow.addView(endInput, LinearLayout.LayoutParams(0, 55, 1f))
        root.addView(trimRow)

        addText(
            "Timeline, split, join, music and branded export are planned for the next stages.",
            13f,
            Color.LTGRAY
        )

        addButton("BACK TO RECORDING") {
            finish()
        }

        val scrollView = ScrollView(this)
        scrollView.addView(root)
        setContentView(scrollView)
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        controls.player = null
        player.release()
        super.onDestroy()
    }
}
