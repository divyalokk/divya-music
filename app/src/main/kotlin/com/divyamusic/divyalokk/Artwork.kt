package com.divyamusic.divyalokk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color


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


// ----------------------------------------------------
// ARTWORK COLOURS
// ----------------------------------------------------

private const val HUE_BINS = 12

/**
 * Picks the most common saturated hue in the artwork and builds the glow palette from it.
 * Returns null for artwork without colour (greys, black and white), so the default palette stays.
 */
fun extractGlowPalette(bitmap: Bitmap): GlowPalette? {

    val size = 32

    val sample = Bitmap.createScaledBitmap(bitmap, size, size, true)

    val pixels = IntArray(size * size)

    sample.getPixels(pixels, 0, size, 0, 0, size, size)

    if (sample !== bitmap) {
        sample.recycle()
    }

    val binWidth = 360f / HUE_BINS

    val weight = FloatArray(HUE_BINS)
    val satWeighted = FloatArray(HUE_BINS)

    val hsv = FloatArray(3)

    for (pixel in pixels) {

        android.graphics.Color.colorToHSV(pixel, hsv)

        val saturation = hsv[1]
        val value = hsv[2]

        // Ignore washed-out and very dark pixels: they say nothing about the hue
        if (saturation < 0.25f || value < 0.2f) continue

        val bin = (hsv[0] / binWidth).toInt().coerceIn(0, HUE_BINS - 1)

        val w = saturation * value

        weight[bin] += w
        satWeighted[bin] += saturation * w
    }

    val best = weight.indices.maxByOrNull { weight[it] } ?: return null

    if (weight[best] <= 0f) return null

    val hue = best * binWidth + binWidth / 2f

    val saturation = (satWeighted[best] / weight[best]).coerceIn(0.45f, 0.9f)

    return GlowPalette(
        primary = hsvColor(hue, saturation, 0.95f),
        secondary = hsvColor(hue + 40f, saturation * 0.9f, 0.9f),
        tertiary = hsvColor(hue - 60f, saturation, 0.6f)
    )
}

private fun hsvColor(hue: Float, saturation: Float, value: Float): Color {

    val wrapped = ((hue % 360f) + 360f) % 360f

    val hsv = floatArrayOf(
        wrapped,
        saturation.coerceIn(0f, 1f),
        value.coerceIn(0f, 1f)
    )

    return Color(android.graphics.Color.HSVToColor(hsv))
}
