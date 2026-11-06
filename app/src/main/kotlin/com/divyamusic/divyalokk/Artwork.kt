package com.divyamusic.divyalokk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri


// ----------------------------------------------------
// ARTWORK
// ----------------------------------------------------

/** Longest side, in pixels, of the decoded artwork. Larger keeps the thumbnail sharp. */
private const val MAX_ARTWORK_PX = 1080

/** Reads the embedded album picture of a track at up to [MAX_ARTWORK_PX] per side. */
fun loadArtwork(context: Context, uri: Uri): Bitmap? {

    val retriever = MediaMetadataRetriever()

    return try {

        retriever.setDataSource(context, uri)

        val bytes = retriever.embeddedPicture ?: return null

        // First read only the size, then pick the largest power-of-two step that stays sharp
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)

        var sample = 1
        while (largest / (sample * 2) >= MAX_ARTWORK_PX) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    } catch (e: Exception) {

        null

    } finally {

        retriever.release()
    }
}
