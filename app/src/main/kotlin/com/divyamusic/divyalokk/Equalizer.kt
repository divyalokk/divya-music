package com.divyamusic.divyalokk

import android.media.audiofx.Equalizer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt


// ----------------------------------------------------
// AUDIO EQUALIZER STATE
// ----------------------------------------------------

/**
 * Wraps android.media.audiofx.Equalizer attached to the MediaPlayer session.
 * Band levels are in millibels, as the platform reports them.
 */
class AudioEqualizer {

    private var effect: Equalizer? = null

    var isOn by mutableStateOf(false)
        private set

    var available by mutableStateOf(false)
        private set

    var minLevel by mutableIntStateOf(-1500)
        private set

    var maxLevel by mutableIntStateOf(1500)
        private set

    var currentPreset by mutableIntStateOf(-1)
        private set

    /** True while wired headphones or a Bluetooth device are the audio output. */
    var outputConnected by mutableStateOf(false)
        private set

    /** Applies the equalizer only when headphones or Bluetooth are connected. */
    fun updateOutput(connected: Boolean) {

        outputConnected = connected

        try {
            effect?.enabled = isOn && connected
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val bandLevels = mutableStateListOf<Int>()

    val bandFrequencies = mutableStateListOf<Int>()

    val presetNames = mutableStateListOf<String>()


    // Attach to a new playback session; saved levels are re-applied
    fun attach(audioSessionId: Int) {

        release()

        try {

            val eq = Equalizer(0, audioSessionId)

            effect = eq

            val count = eq.numberOfBands.toInt()

            val range = eq.bandLevelRange

            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()

            bandFrequencies.clear()

            for (i in 0 until count) {
                // getCenterFreq reports milliHertz
                bandFrequencies.add(eq.getCenterFreq(i.toShort()) / 1000)
            }

            if (bandLevels.size != count) {

                bandLevels.clear()

                for (i in 0 until count) {
                    bandLevels.add(eq.getBandLevel(i.toShort()).toInt())
                }
            }

            for (i in 0 until count) {
                eq.setBandLevel(i.toShort(), bandLevels[i].toShort())
            }

            presetNames.clear()

            for (p in 0 until eq.numberOfPresets.toInt()) {
                presetNames.add(eq.getPresetName(p.toShort()))
            }

            eq.enabled = isOn && outputConnected

            available = true

        } catch (e: Exception) {

            e.printStackTrace()

            effect = null
            available = false
        }
    }

    fun toggle(on: Boolean) {

        isOn = on

        try {
            effect?.enabled = on && outputConnected
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBand(index: Int, level: Int) {

        if (index !in bandLevels.indices) return

        val clamped = level.coerceIn(minLevel, maxLevel)

        bandLevels[index] = clamped

        try {
            effect?.setBandLevel(index.toShort(), clamped.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun usePreset(index: Int) {

        val eq = effect ?: return

        try {

            eq.usePreset(index.toShort())

            currentPreset = index

            for (i in bandLevels.indices) {
                bandLevels[i] = eq.getBandLevel(i.toShort()).toInt()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun flatten() {

        for (i in bandLevels.indices) {
            setBand(i, 0)
        }

        currentPreset = -1
    }

    fun release() {

        try {
            effect?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        effect = null
        available = false
    }
}


// ----------------------------------------------------
// EQUALIZER PAGE
// ----------------------------------------------------

/** Full Equalizer page, opened from its own button. Back returns to the previous page. */
@Composable
fun EqualizerScreen(
    equalizer: AudioEqualizer,
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            AppIconButton(onClick = onBack) {

                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        EqualizerSection(
            equalizer = equalizer,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}


// ----------------------------------------------------
// EQUALIZER SECTION UI
// ----------------------------------------------------

@Composable
fun EqualizerSection(
    equalizer: AudioEqualizer,
    modifier: Modifier = Modifier
) {

    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        contentPadding = 20.dp
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Switch(
                checked = equalizer.isOn,
                onCheckedChange = { equalizer.toggle(it) },
                // Turning on needs a song and headphones/Bluetooth; turning off is always allowed
                enabled = equalizer.available && (equalizer.outputConnected || equalizer.isOn),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        if (!equalizer.available) {

            Text(
                text = "Play a song to apply the equalizer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            return@AppCard
        }

        if (!equalizer.outputConnected) {

            Text(
                text = "Connect wired headphones or a Bluetooth device to use the equalizer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // PRESETS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            equalizer.presetNames.forEachIndexed { index, name ->

                EqualizerChip(
                    name = name,
                    selected = equalizer.currentPreset == index,
                    enabled = equalizer.isOn,
                    onClick = { equalizer.usePreset(index) }
                )
            }

            EqualizerChip(
                name = "Reset",
                selected = false,
                enabled = equalizer.isOn,
                onClick = { equalizer.flatten() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // BANDS
        equalizer.bandFrequencies.forEachIndexed { index, hz ->

            val level = equalizer.bandLevels.getOrElse(index) { 0 }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = formatFrequency(hz),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp)
                )

                Slider(
                    value = level.toFloat(),
                    onValueChange = { equalizer.setBand(index, it.roundToInt()) },
                    valueRange = equalizer.minLevel.toFloat()..equalizer.maxLevel.toFloat(),
                    enabled = equalizer.isOn,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "%.1f dB".format(level / 100f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp)
                )
            }
        }
    }
}

@Composable
private fun EqualizerChip(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {

    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {

        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface
        )
    }
}

fun formatFrequency(hz: Int): String {
    return if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
}
