package com.divyamusic.divyalokk

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


// ----------------------------------------------------
// PAGES
// ----------------------------------------------------

/** The top-level pages that the home screen can switch to. */
enum class AppPage { Home, About, Equalizer, Settings }


// ----------------------------------------------------
// SETTINGS STORE
// ----------------------------------------------------

/** Theme switches, saved in SharedPreferences and read back on the next launch. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("divya_settings", Context.MODE_PRIVATE)

    /** Colours from the system wallpaper palette (Android 12+). */
    var materialDynamic by mutableStateOf(prefs.getBoolean(KEY_MATERIAL, false))
        private set

    /** Glow and accent colours taken from the current song's artwork. */
    var liquidDynamic by mutableStateOf(prefs.getBoolean(KEY_LIQUID, false))
        private set

    fun updateMaterialDynamic(on: Boolean) {

        materialDynamic = on

        prefs.edit().putBoolean(KEY_MATERIAL, on).apply()
    }

    fun updateLiquidDynamic(on: Boolean) {

        liquidDynamic = on

        prefs.edit().putBoolean(KEY_LIQUID, on).apply()
    }

    private companion object {
        const val KEY_MATERIAL = "material_dynamic"
        const val KEY_LIQUID = "liquid_dynamic"
    }
}

@Composable
fun rememberAppSettings(): AppSettings {

    val context = LocalContext.current.applicationContext

    return remember(context) { AppSettings(context) }
}


// ----------------------------------------------------
// SETTINGS SCREEN
// ----------------------------------------------------

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit
) {

    val canUseMaterialDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {

        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            GlassIconButton(onClick = onBack) {

                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ThemeSwitchCard(
            title = "Material dynamic theme",
            description = if (canUseMaterialDynamic) {
                "Colour the app with the palette of your wallpaper."
            } else {
                "Needs Android 12 or newer."
            },
            checked = settings.materialDynamic && canUseMaterialDynamic,
            enabled = canUseMaterialDynamic,
            onCheckedChange = { settings.updateMaterialDynamic(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ThemeSwitchCard(
            title = "Liquid glass dynamic theme",
            description = "Tint the glow and accents with the colours of the current song's " +
                "artwork. Takes priority over Material dynamic when both are on.",
            checked = settings.liquidDynamic,
            enabled = true,
            onCheckedChange = { settings.updateLiquidDynamic(it) }
        )
    }
}

@Composable
private fun ThemeSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        strength = 0.16f,
        contentPadding = 20.dp
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = divyaAccent()
                )
            )
        }
    }
}
