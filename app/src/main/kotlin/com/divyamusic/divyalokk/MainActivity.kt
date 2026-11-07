package com.divyamusic.divyalokk

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen


// ====================================================
// MAIN ACTIVITY
// ====================================================

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

                onSeek = { position -> Playback.seekTo(position) },

                shuffle = Playback.shuffle,

                repeatMode = Playback.repeatMode,

                onToggleShuffle = { Playback.toggleShuffle() },

                onCycleRepeat = { Playback.cycleRepeat() }
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
