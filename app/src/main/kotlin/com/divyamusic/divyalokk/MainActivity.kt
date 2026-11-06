package com.divyamusic.divyalokk

import android.Manifest
import android.graphics.Bitmap
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.divyamusic.divyalokk.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


// ----------------------------------------------------
// SONG DATA
// ----------------------------------------------------

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri
)

// ----------------------------------------------------
// MAIN ACTIVITY
// ----------------------------------------------------

class MainActivity : ComponentActivity() {

    // Playback lives in the shared Playback store (see Playback.kt)


    // ------------------------------------------------
    // PERMISSION
    // ------------------------------------------------

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                loadMusic()
            }

            requestNotificationPermission()
        }

    // Android 13+ needs this to show the playback notification (song controls)
    private val notificationLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }


    // ------------------------------------------------
    // ON CREATE
    // ------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        Playback.init(this)

        setContent {

            MusicApp(
                songs = Playback.songs,
                currentIndex = Playback.currentIndex,
                isPlaying = Playback.isPlaying,
                currentPosition = Playback.currentPosition,
                duration = Playback.duration,

                equalizer = Playback.equalizer,

                // Real playback position from the player
                getCurrentPosition = { Playback.livePosition() },

                // Update Compose position
                onPositionChange = { position ->
                    Playback.currentPosition = position
                },

                onSongClick = { index -> Playback.playSong(index) },

                onPlayPause = { Playback.playPause() },

                onPrevious = { Playback.previous() },

                onNext = { Playback.next() },

                onSeek = { position -> Playback.seekTo(position) }
            )
        }

        checkPermission()
    }


    // ------------------------------------------------
    // CHECK PERMISSION
    // ------------------------------------------------

    private fun checkPermission() {

        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                Manifest.permission.READ_MEDIA_AUDIO

            } else {

                Manifest.permission.READ_EXTERNAL_STORAGE
            }


        if (
            ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {

            loadMusic()
            requestNotificationPermission()

        } else {

            permissionLauncher.launch(permission)
        }
    }


    // ------------------------------------------------
    // NOTIFICATION PERMISSION (Android 13+)
    // ------------------------------------------------

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {

            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }


    // ------------------------------------------------
    // LOAD DEVICE MUSIC
    // ------------------------------------------------

    private fun loadMusic() {

        val collection =
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI


        val projection = arrayOf(

            MediaStore.Audio.Media._ID,

            MediaStore.Audio.Media.TITLE,

            MediaStore.Audio.Media.ARTIST
        )


        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"


        val newSongs =
            mutableListOf<Song>()


        contentResolver.query(

            collection,

            projection,

            selection,

            null,

            "${MediaStore.Audio.Media.TITLE} ASC"

        )?.use { cursor ->


            val idColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID
                )


            val titleColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE
                )


            val artistColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST
                )


            while (cursor.moveToNext()) {

                val id =
                    cursor.getLong(idColumn)


                // MediaStore can return null for tags that are missing
                val title =
                    cursor.getString(titleColumn)
                        ?: "Unknown title"


                val artist =
                    cursor.getString(artistColumn)
                        ?: "Unknown artist"


                val uri =
                    ContentUris.withAppendedId(
                        collection,
                        id
                    )


                newSongs.add(

                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        uri = uri
                    )
                )
            }
        }


        Playback.loadSongs(newSongs)
    }


}


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

    onSeek: (Int) -> Unit
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
                    onClose = { showPlayer = false }
                )
            }

            // In-app update check (GitHub releases)
            UpdateChecker()
        }
    }
}


// ====================================================
// HOME SCREEN
// ====================================================

@Composable
private fun BoxScope.HomeContent(
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
