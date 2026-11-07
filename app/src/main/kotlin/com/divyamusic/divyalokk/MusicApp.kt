package com.divyamusic.divyalokk

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ====================================================
// MUSIC APP UI
// ====================================================

@Composable
fun MusicApp(

    songs: List<Song>,

    currentIndex: Int,

    isPlaying: Boolean,

    currentPosition: Int,

    duration: Int,

    equalizer: AudioEqualizer,

    getCurrentPosition: () -> Int,

    onPositionChange: (Int) -> Unit,

    onSongClick: (Int) -> Unit,

    onPlayPause: () -> Unit,

    onPrevious: () -> Unit,

    onNext: () -> Unit,

    onSeek: (Int) -> Unit,

    shuffle: Boolean = false,

    repeatMode: Int = Playback.REPEAT_OFF,

    onToggleShuffle: () -> Unit = {},

    onCycleRepeat: () -> Unit = {}
) {


    // ------------------------------------------------
    // REAL TIME MEDIA PLAYER POSITION
    // ------------------------------------------------

    LaunchedEffect(
        isPlaying,
        currentIndex
    ) {

        while (isPlaying) {

            val position =
                getCurrentPosition()


            onPositionChange(
                position
            )


            delay(300)
        }
    }


    // ------------------------------------------------
    // CURRENT SONG
    // ------------------------------------------------

    val currentSong =

        if (
            currentIndex >= 0 &&
            currentIndex < songs.size
        ) {

            songs[currentIndex]

        } else {

            null
        }


    // ------------------------------------------------
    // SCREEN STATE
    // ------------------------------------------------

    // Top-level page shown under the player: home, about, equalizer or settings
    var page by rememberSaveable { mutableStateOf(AppPage.Home) }

    // Full-screen player, opened by swiping up on the now-playing card
    var showPlayer by rememberSaveable { mutableStateOf(false) }

    val playerProgress by animateFloatAsState(
        targetValue = if (showPlayer) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "playerProgress"
    )

    val settings = rememberAppSettings()

    val context = LocalContext.current

    // Cover of the current song, decoded off the main thread and shared by the player
    val art by produceState<Bitmap?>(null, currentSong?.uri) {

        value = null

        val song = currentSong ?: return@produceState

        value = withContext(Dispatchers.IO) {

            loadArtwork(context, song.uri)
        }
    }

    BackHandler(enabled = page != AppPage.Home) {
        page = AppPage.Home
    }

    BackHandler(enabled = showPlayer) {
        showPlayer = false
    }


    DivyaTheme(
        materialDynamic = settings.materialDynamic
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            // Pages cross-fade when switched
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 300))
                },
                modifier = Modifier.fillMaxSize(),
                label = "pageFade"
            ) { target ->

                when (target) {

                    AppPage.Home -> Box(modifier = Modifier.fillMaxSize()) {
                        HomeContent(
                            songs = songs,
                            currentIndex = currentIndex,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            currentPosition = currentPosition,
                            duration = duration,
                            onSongClick = onSongClick,
                            onPlayPause = onPlayPause,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onSeek = onSeek,
                            onAbout = { page = AppPage.About },
                            onSettings = { page = AppPage.Settings },
                            onOpenPlayer = { showPlayer = true }
                        )
                    }

                    AppPage.About -> AboutScreen(
                        onBack = { page = AppPage.Home }
                    )

                    AppPage.Equalizer -> EqualizerScreen(
                        equalizer = equalizer,
                        onBack = { page = AppPage.Home }
                    )

                    AppPage.Settings -> SettingsScreen(
                        settings = settings,
                        onBack = { page = AppPage.Home }
                    )
                }
            }

            if (playerProgress > 0f) {

                FullPlayerScreen(
                    currentSong = currentSong,
                    artwork = art,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    progress = playerProgress,
                    onSeek = onSeek,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    // The Equalizer page sits under the player, so close the player first
                    onOpenEqualizer = {
                        showPlayer = false
                        page = AppPage.Equalizer
                    },
                    onClose = { showPlayer = false },
                    shuffle = shuffle,
                    repeatMode = repeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat
                )
            }

            // In-app update check (GitHub releases)
            UpdateChecker()
        }
    }
}


// ====================================================
// FORMAT TIME
// ====================================================

fun formatTime(
    milliseconds: Int
): String {

    val totalSeconds =
        milliseconds / 1000


    val minutes =
        totalSeconds / 60


    val seconds =
        totalSeconds % 60


    return "%d:%02d".format(
        minutes,
        seconds
    )
}


// ====================================================
// PREVIEW
// ====================================================

@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun MusicAppPreview() {

    val previewSongs =
        listOf(

            Song(
                1,
                "My Song",
                "Artist",
                Uri.EMPTY
            ),

            Song(
                2,
                "Another Song",
                "Singer",
                Uri.EMPTY
            ),

            Song(
                3,
                "Favorite Music",
                "Unknown",
                Uri.EMPTY
            )
        )


    MusicApp(

        songs =
            previewSongs,

        currentIndex =
            0,

        isPlaying =
            false,

        currentPosition =
            45_000,

        duration =
            200_000,

        equalizer =
            AudioEqualizer(),

        getCurrentPosition =
            {
                45_000
            },

        onPositionChange =
            {},

        onSongClick =
            {},

        onPlayPause =
            {},

        onPrevious =
            {},

        onNext =
            {},

        onSeek =
            {}
    )
}
