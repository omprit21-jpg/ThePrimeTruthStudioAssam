
package com.theprimetruth.studio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class EditorActivity : AppCompatActivity() {

    private lateinit var videoPreview: VideoView
    private lateinit var statusText: TextView
    private var selectedVideo: Uri? = null

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedVideo = uri
                videoPreview.setVideoURI(uri)
                statusText.text = "Video imported successfully"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
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

        videoPreview = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        root.addView(videoPreview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        statusText = TextView(this).apply {
            text = "Import a video to begin editing"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
        }
        root.addView(statusText)

        addButton("IMPORT VIDEO") {
            videoPicker.launch("video/*")
        }

        addButton("PLAY / PAUSE") {
            if (selectedVideo == null) {
                statusText.text = "Please import a video first"
            } else if (videoPreview.isPlaying) {
                videoPreview.pause()
            } else {
                videoPreview.start()
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
            "Timeline, split, join, music and branded export will be added in the next stages.",
            13f,
            Color.LTGRAY
        )

        addButton("BACK TO RECORDING") {
            finish()
        }

        setContentView(root)
    }

    override fun onPause() {
        super.onPause()
        if (::videoPreview.isInitialized && videoPreview.isPlaying) {
            videoPreview.pause()
        }
    }
}
