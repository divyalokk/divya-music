package com.divyamusic.divyalokk

import android.net.Uri

// ----------------------------------------------------
// SONG DATA
// ----------------------------------------------------

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri
)
