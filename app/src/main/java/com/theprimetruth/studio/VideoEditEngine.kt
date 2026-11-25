
package com.theprimetruth.studio

import android.net.Uri
import androidx.media3.common.MediaItem

/**
 * A single clip in The Prime Truth Studio editing timeline.
 * Times are stored in milliseconds.
 */
data class VideoClip(
    val id: Long,
    val uri: Uri,
    val startMs: Long = 0L,
    val endMs: Long? = null
) {
    fun durationMs(sourceDurationMs: Long): Long {
        val end = endMs ?: sourceDurationMs
        return (end - startMs).coerceAtLeast(0L)
    }

    fun toMediaItem(): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)

        if (startMs > 0L || endMs != null) {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .apply {
                    endMs?.let { setEndPositionMs(it) }
                }
                .build()

            builder.setClippingConfiguration(clipping)
        }

        return builder.build()
    }
}

/**
 * Timeline operations: add, trim, split, remove and reorder clips.
 */
class VideoEditEngine {

    private val clips = mutableListOf<VideoClip>()

    fun addClip(uri: Uri): List<VideoClip> {
        clips.add(
            VideoClip(
                id = System.nanoTime(),
                uri = uri
            )
        )
        return getClips()
    }

    fun getClips(): List<VideoClip> = clips.toList()

    fun removeClip(id: Long): List<VideoClip> {
        clips.removeAll { it.id == id }
        return getClips()
    }

    fun clear(): List<VideoClip> {
        clips.clear()
        return getClips()
    }

    fun trimClip(
        id: Long,
        startMs: Long,
        endMs: Long
    ): Boolean {
        val index = clips.indexOfFirst { it.id == id }
        if (index == -1 || startMs < 0L || endMs <= startMs) {
            return false
        }

        clips[index] = clips[index].copy(
            startMs = startMs,
            endMs = endMs
        )
        return true
    }

    fun splitClip(
        id: Long,
        splitAtMs: Long
    ): Boolean {
        val index = clips.indexOfFirst { it.id == id }
        if (index == -1) return false

        val original = clips[index]
        val end = original.endMs ?: return false

        if (splitAtMs <= original.startMs || splitAtMs >= end) {
            return false
        }

        val firstPart = original.copy(
            endMs = splitAtMs
        )

        val secondPart = original.copy(
            id = System.nanoTime(),
            startMs = splitAtMs
        )

        clips[index] = firstPart
        clips.add(index + 1, secondPart)

        return true
    }

    fun moveClip(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in clips.indices || toIndex !in clips.indices) {
            return false
        }

        val clip = clips.removeAt(fromIndex)
        clips.add(toIndex, clip)
        return true
    }
}
