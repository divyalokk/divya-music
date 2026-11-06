package com.divyamusic.divyalokk

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


// ----------------------------------------------------
// GESTURES
// ----------------------------------------------------

/** Calls [onSwipe] with the total vertical drag distance (negative = up) when a drag ends. */
fun Modifier.onVerticalSwipe(onSwipe: (Float) -> Unit): Modifier =
    pointerInput(onSwipe) {

        var total = 0f

        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { onSwipe(total) },
            onDragCancel = { total = 0f }
        ) { _, dragAmount ->

            total += dragAmount
        }
    }


// ----------------------------------------------------
// FULL PLAYER
// ----------------------------------------------------

/** Blur needs Android 12 (API 31) or newer; older versions show a plain gradient instead. */
private val BlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Full-screen player. [progress] runs from 0 (hidden, below the screen) to 1 (fully open),
 * and is driven by the caller so the slide and fade animation lives in one place.
 * [artwork] is the current track's cover, loaded once by the caller and shared with the theme.
 */
@Composable
fun FullPlayerScreen(
    currentSong: Song?,
    artwork: Bitmap?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    progress: Float,
    onSeek: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onClose: () -> Unit
) {

    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }

    val safeDuration = duration.coerceAtLeast(1)

    val safePosition = currentPosition.coerceIn(0, safeDuration)

    val scheme = MaterialTheme.colorScheme

    val blurred = artwork != null && BlurSupported

    // With blur: a dark tint over the cover. Without: a gradient in the theme colours.
    val scrim = if (blurred) {
        Brush.verticalGradient(
            listOf(
                scheme.background.copy(alpha = 0.6f),
                scheme.background.copy(alpha = 0.9f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                scheme.primaryContainer,
                scheme.background
            )
        )
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            // Slide up while fading in; both follow the same progress value
            .graphicsLayer {
                translationY = (1f - progress) * screenHeightPx
                alpha = progress
            }
            .background(scheme.background)
            // Swallow taps so they do not reach the home screen behind
            .pointerInput(Unit) { detectTapGestures { } }
    ) {

        // BLURRED THUMBNAIL BACKDROP (Android 12+ only)
        if (artwork != null && blurred) {

            Image(
                bitmap = artwork.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
            )
        }

        // Tint so text stays readable over any artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrim)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            // HEADER: swipe down or tap to close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onVerticalSwipe { total -> if (total > 120f) onClose() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                AppIconButton(onClick = onClose) {

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close player"
                    )
                }

                Text(
                    text = "Now playing",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.size(44.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(16.dp))

                // ARTWORK
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(scheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {

                    if (artwork != null) {

                        Image(
                            bitmap = artwork.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize()
                        )

                    } else {

                        Icon(
                            painter = painterResource(R.drawable.music),
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // TITLE
                Column(modifier = Modifier.fillMaxWidth()) {

                    Text(
                        text = currentSong?.title ?: "Select a song",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = currentSong?.artist ?: "Tap a song to play",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SEEK
                Slider(
                    value = safePosition.toFloat(),
                    onValueChange = { value -> onSeek(value.toInt()) },
                    valueRange = 0f..safeDuration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = formatTime(safePosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )

                    Text(
                        text = formatTime(safeDuration),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    AppIconButton(
                        onClick = onPrevious,
                        buttonSize = 60.dp
                    ) {

                        Icon(
                            painter = painterResource(R.drawable.next),
                            contentDescription = "Previous",
                            modifier = Modifier.rotate(180f)
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {

                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    AppIconButton(
                        onClick = onNext,
                        buttonSize = 60.dp
                    ) {

                        Icon(
                            painter = painterResource(R.drawable.next),
                            contentDescription = "Next"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // EQUALIZER PAGE BUTTON
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surfaceContainerHigh)
                        .clickable(onClick = onOpenEqualizer)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Equalizer",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
