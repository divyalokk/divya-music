package com.divyamusic.divyalokk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ====================================================
// HOME SCREEN
// ====================================================

@Composable
internal fun BoxScope.HomeContent(
    songs: List<Song>,
    currentIndex: Int,
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onSongClick: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onAbout: () -> Unit,
    onSettings: () -> Unit,
    onOpenPlayer: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {

        // ========================================
        // HEADER
        // ========================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {

                Text(
                    text = "DivyaMusic",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "${songs.size} songs on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AppIconButton(onClick = onSettings) {

                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            AppIconButton(onClick = onAbout) {

                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))


        if (songs.isEmpty()) {

            // ====================================
            // EMPTY STATE
            // ====================================

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {

                AppCard(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = 24.dp
                ) {

                    Text(
                        text = "No music found",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )

                    Text(
                        text = "Songs on this device will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        } else {

            // ====================================
            // SONG LIST
            // ====================================

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {

                itemsIndexed(songs) { index, song ->

                    SongRow(
                        song = song,
                        selected = index == currentIndex,
                        onClick = { onSongClick(index) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))


            // ====================================
            // NOW PLAYING
            // ====================================

            NowPlayingCard(
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onSeek = onSeek,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onOpenPlayer = onOpenPlayer
            )
        }
    }
}


// ====================================================
// SONG ROW
// ====================================================

@Composable
private fun SongRow(
    song: Song,
    selected: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            painter = painterResource(R.drawable.music),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(28.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {

            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


// ====================================================
// NOW PLAYING CARD
// ====================================================

@Composable
private fun NowPlayingCard(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onSeek: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit
) {

    val safeDuration =
        duration.coerceAtLeast(1)

    val safePosition =
        currentPosition.coerceIn(
            0,
            safeDuration
        )

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            // Swipe up to open the full-screen player
            .onVerticalSwipe { total -> if (total < -80f) onOpenPlayer() },
        shape = RoundedCornerShape(32.dp),
        contentPadding = 20.dp
    ) {

        Text(
            text = currentSong?.title ?: "Select a song",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = currentSong?.artist ?: "Tap a song to play",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))


        // SEEK SLIDER
        Slider(
            value = safePosition.toFloat(),
            onValueChange = { value ->
                onSeek(value.toInt())
            },
            valueRange = 0f..safeDuration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )


        // TIME
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

        Spacer(modifier = Modifier.height(12.dp))


        // CONTROLS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {

            AppIconButton(
                onClick = onPrevious,
                buttonSize = 52.dp
            ) {

                Icon(
                    painter = painterResource(R.drawable.next),
                    contentDescription = "Previous",
                    modifier = Modifier.rotate(180f),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // PLAY / PAUSE
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            AppIconButton(
                onClick = onNext,
                buttonSize = 52.dp
            ) {

                Icon(
                    painter = painterResource(R.drawable.next),
                    contentDescription = "Next",
                    tint = Color.White
                )
            }
        }
    }
}
