package com.divyamusic.divyalokk

import android.graphics.Bitmap
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    val accent = divyaAccent()


    Box(
        modifier = Modifier
            .fillMaxSize()
            // Slide up while fading in; both follow the same progress value
            .graphicsLayer {
                translationY = (1f - progress) * screenHeightPx
                alpha = progress
            }
            .background(Color(0xFF0B0B14))
            // Swallow taps so they do not reach the home screen behind
            .pointerInput(Unit) { detectTapGestures { } }
    ) {

        // BLURRED THUMBNAIL BACKDROP
        artwork?.let { bitmap ->

            Image(
                bitmap = bitmap.asImageBitmap(),
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
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x991B0B22),
                            Color(0xE60B0B14)
                        )
                    )
                )
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

                GlassIconButton(onClick = onClose) {

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close player",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Now playing",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
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
                        .glass(RoundedCornerShape(32.dp), strength = 0.1f),
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
                            tint = AccentCoral,
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
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = currentSong?.artist ?: "Tap a song to play",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = formatTime(safePosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = formatTime(safeDuration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    GlassIconButton(
                        onClick = onPrevious,
                        buttonSize = 60.dp
                    ) {

                        Icon(
                            painter = painterResource(R.drawable.next),
                            contentDescription = "Previous",
                            modifier = Modifier.rotate(180f),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {

                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    GlassIconButton(
                        onClick = onNext,
                        buttonSize = 60.dp
                    ) {

                        Icon(
                            painter = painterResource(R.drawable.next),
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // EQUALIZER PAGE BUTTON
                Box(
                    modifier = Modifier
                        .glass(RoundedCornerShape(50), strength = 0.16f)
                        .clickable(onClick = onOpenEqualizer)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Equalizer",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
