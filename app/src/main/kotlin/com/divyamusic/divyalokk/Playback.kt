package com.divyamusic.divyalokk

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random


// ----------------------------------------------------
// PLAYBACK STORE
// ----------------------------------------------------

/**
 * Shared playback state. It lives in the process so the Activity and [PlaybackService]
 * (notification controls) see the same player. The song list, current song, position and
 * duration are saved to SharedPreferences and restored on the next launch.
 */
object Playback {

    private const val PREFS = "playback_state"
    private const val KEY_SONGS = "songs"
    private const val KEY_INDEX = "index"
    private const val KEY_DURATION = "duration"
    private const val KEY_POSITION = "position"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_REPEAT = "repeat"

    const val REPEAT_OFF = 0
    const val REPEAT_ALL = 1
    const val REPEAT_ONE = 2

    val songs = mutableStateListOf<Song>()

    var shuffle by mutableStateOf(false)
        private set

    var repeatMode by mutableIntStateOf(REPEAT_OFF)
        private set

    var currentIndex by mutableIntStateOf(-1)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var currentPosition by mutableIntStateOf(0)

    var duration by mutableIntStateOf(1)
        private set

    val equalizer = AudioEqualizer()

    private var player: MediaPlayer? = null

    private var appContext: Context? = null

    private var prefs: SharedPreferences? = null

    private var audioManager: AudioManager? = null

    // True while this app holds audio focus
    private var hasFocus = false

    // True when playback was paused by a transient focus loss (call, other app), so it can resume
    private var pausedByFocusLoss = false

    // Volume multiplier: 1f normally, lowered while another app ducks our audio
    private var duck = 1f

    // Position to resume from when the restored song is started again
    private var resumePosition = 0

    /** Called after every state change so the notification can refresh. */
    var onStateChanged: (() -> Unit)? = null


    // ------------------------------------------------
    // SETUP
    // ------------------------------------------------

    fun init(context: Context) {

        if (appContext != null) return

        val app = context.applicationContext

        appContext = app

        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        restore()

        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioManager = audio

        // Pause when the headphones are unplugged (speaker would otherwise start playing loudly)
        ContextCompat.registerReceiver(
            app,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        equalizer.updateOutput(hasHeadphones(audio))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            audio.registerAudioDeviceCallback(
                object : AudioDeviceCallback() {

                    override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
                        equalizer.updateOutput(hasHeadphones(audio))
                    }

                    override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
                        equalizer.updateOutput(hasHeadphones(audio))
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isPlaying) {
                pausedByFocusLoss = false
                playPause()
            }
        }
    }

    // Audio focus: other apps and calls can pause us, lower our volume, or hand focus back
    @Suppress("DEPRECATION")
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {

            AudioManager.AUDIOFOCUS_GAIN -> {
                duck = 1f
                player?.setVolume(1f, 1f)
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    if (!isPlaying) playPause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duck = 0.2f
                player?.setVolume(duck, duck)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) {
                    pausedByFocusLoss = true
                    playPause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocusLoss = false
                if (isPlaying) playPause()
                abandonFocus()
            }
        }
    }

    /** Asks for audio focus before playing. Returns true when granted. */
    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val audio = audioManager ?: return true
        hasFocus = audio.requestAudioFocus(
            focusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager?.abandonAudioFocus(focusListener)
        hasFocus = false
    }

    /** True when wired headphones or a Bluetooth audio device are connected. */
    @SuppressLint("NewApi") // API 23+ calls are only reached after the SDK check below
    private fun hasHeadphones(audio: AudioManager): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            return audio.isWiredHeadsetOn || audio.isBluetoothA2dpOn
        }

        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET)
        }
    }


    // ------------------------------------------------
    // SONG LIST
    // ------------------------------------------------

    /** Replaces the list from MediaStore and keeps the current song selected if it still exists. */
    fun loadSongs(newSongs: List<Song>) {

        val currentUri = songs.getOrNull(currentIndex)?.uri

        songs.clear()
        songs.addAll(newSongs)

        currentIndex = if (currentUri == null) -1 else songs.indexOfFirst { it.uri == currentUri }

        save()
    }


    // ------------------------------------------------
    // PLAYBACK
    // ------------------------------------------------

    fun playSong(index: Int) {

        if (index !in songs.indices) return

        val context = appContext ?: return

        // Resume only when the restored song is started again
        val start = if (index == currentIndex) resumePosition else 0

        resumePosition = 0

        player?.release()
        player = null

        currentIndex = index
        currentPosition = 0
        duration = 1

        try {

            val newPlayer = MediaPlayer.create(context, songs[index].uri)

            if (newPlayer == null) {

                isPlaying = false

            } else {

                player = newPlayer

                duration = newPlayer.duration.coerceAtLeast(1)

                if (start > 0) {
                    newPlayer.seekTo(start)
                    currentPosition = start
                }

                newPlayer.setOnCompletionListener { onTrackFinished() }

                newPlayer.setVolume(duck, duck)

                // Apply the equalizer to this track's audio session
                equalizer.attach(newPlayer.audioSessionId)

                if (requestFocus()) {
                    newPlayer.start()
                    isPlaying = true
                } else {
                    isPlaying = false
                }

                PlaybackService.start(context)
            }

        } catch (e: Exception) {

            e.printStackTrace()

            isPlaying = false
        }

        save()
        changed()
    }

    fun playPause() {

        val current = player

        if (current == null) {

            if (songs.isNotEmpty()) {
                playSong(if (currentIndex in songs.indices) currentIndex else 0)
            }

            return
        }

        try {

            pausedByFocusLoss = false

            if (current.isPlaying) {

                current.pause()
                isPlaying = false
                currentPosition = livePosition()

                // Let other apps take focus back while paused
                abandonFocus()

            } else {

                if (requestFocus()) {
                    current.start()
                    isPlaying = true
                }
            }

        } catch (e: Exception) {

            e.printStackTrace()

            isPlaying = false
        }

        savePlayerState()
        changed()
    }

    /** Next track in play order: random when shuffle is on, otherwise the list order. */
    private fun nextIndex(): Int? {

        if (songs.isEmpty()) return null

        if (shuffle && songs.size > 1) {

            var pick: Int
            do {
                pick = Random.nextInt(songs.size)
            } while (pick == currentIndex)

            return pick
        }

        if (currentIndex < songs.lastIndex) return currentIndex + 1

        return if (repeatMode == REPEAT_ALL) 0 else null
    }

    fun next() {

        if (songs.isEmpty()) return

        val index = nextIndex()

        if (index != null) {

            playSong(index)

        } else {

            // Last song finished and repeat is off
            isPlaying = false
            currentPosition = duration
            abandonFocus()
            save()
            changed()
        }
    }

    /** Called when a track ends: repeat-one replays it, otherwise move to the next track. */
    private fun onTrackFinished() {

        if (repeatMode == REPEAT_ONE && currentIndex in songs.indices) {
            playSong(currentIndex)
        } else {
            next()
        }
    }

    fun toggleShuffle() {

        shuffle = !shuffle
        prefs?.edit()?.putBoolean(KEY_SHUFFLE, shuffle)?.apply()
        changed()
    }

    /** Cycles Off, All, One. */
    fun cycleRepeat() {

        repeatMode = (repeatMode + 1) % 3
        prefs?.edit()?.putInt(KEY_REPEAT, repeatMode)?.apply()
        changed()
    }

    fun previous() {

        if (songs.isEmpty()) return

        if (currentIndex > 0) {

            playSong(currentIndex - 1)

        } else if (currentIndex == 0) {

            // First song: restart from the beginning
            player?.seekTo(0)
            resumePosition = 0
            currentPosition = 0
            save()
            changed()
        }
    }

    fun seekTo(position: Int) {

        val safe = position.coerceIn(0, duration.coerceAtLeast(1))

        val current = player

        if (current != null) {
            current.seekTo(safe)
        } else {
            resumePosition = safe
        }

        currentPosition = safe
        savePlayerState()
        changed()
    }

    /** Position read straight from the player, or the saved one when nothing is loaded. */
    fun livePosition(): Int {

        val current = player ?: return currentPosition

        return runCatching { current.currentPosition }.getOrDefault(currentPosition)
    }

    /** Called by the service while playing, so the position is kept up to date. */
    fun tick() {

        if (!isPlaying) return

        currentPosition = livePosition()

        savePlayerState()
    }

    fun release() {

        abandonFocus()

        runCatching { appContext?.unregisterReceiver(noisyReceiver) }

        equalizer.release()

        player?.release()
        player = null
    }


    // ------------------------------------------------
    // SAVE AND RESTORE
    // ------------------------------------------------

    private fun changed() {
        onStateChanged?.invoke()
    }

    private fun save() {

        val store = prefs ?: return

        val array = JSONArray()

        songs.forEach { song ->
            array.put(
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("uri", song.uri.toString())
            )
        }

        store.edit()
            .putString(KEY_SONGS, array.toString())
            .putInt(KEY_INDEX, currentIndex)
            .putInt(KEY_DURATION, duration)
            .putInt(KEY_POSITION, currentPosition)
            .apply()
    }

    /** Saves only the player fields. The song list is unchanged, so it is not rewritten. */
    private fun savePlayerState() {

        prefs?.edit()
            ?.putInt(KEY_INDEX, currentIndex)
            ?.putInt(KEY_DURATION, duration)
            ?.putInt(KEY_POSITION, currentPosition)
            ?.apply()
    }

    private fun restore() {

        val store = prefs ?: return

        val array = runCatching {
            JSONArray(store.getString(KEY_SONGS, "[]") ?: "[]")
        }.getOrDefault(JSONArray())

        for (i in 0 until array.length()) {

            val item = array.getJSONObject(i)

            songs.add(
                Song(
                    id = item.getLong("id"),
                    title = item.getString("title"),
                    artist = item.getString("artist"),
                    uri = Uri.parse(item.getString("uri"))
                )
            )
        }

        currentIndex = store.getInt(KEY_INDEX, -1).takeIf { it in songs.indices } ?: -1

        duration = store.getInt(KEY_DURATION, 1).coerceAtLeast(1)

        currentPosition = store.getInt(KEY_POSITION, 0)

        resumePosition = currentPosition

        shuffle = store.getBoolean(KEY_SHUFFLE, false)

        repeatMode = store.getInt(KEY_REPEAT, REPEAT_OFF).coerceIn(REPEAT_OFF, REPEAT_ONE)
    }
}
