package com.divyamusic.divyalokk

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


// ----------------------------------------------------
// THEME
// ----------------------------------------------------

/** Used on Android 11 and older, or when Material dynamic theme is off. */
private val FallbackDarkScheme = darkColorScheme(
    primary = Color(0xFFFF2D55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C1A2B),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFFFF9F6B),
    onSecondary = Color(0xFF3D1C00),
    tertiary = Color(0xFF5E60CE),
    onTertiary = Color.White,
    background = Color(0xFF0B0B14),
    onBackground = Color.White,
    surface = Color(0xFF15151F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1F1F2B),
    onSurfaceVariant = Color(0xFFC9C9D6),
    surfaceContainer = Color(0xFF191926),
    surfaceContainerHigh = Color(0xFF22222F)
)

/** How long colours take to fade when the theme changes. */
private const val THEME_FADE_MS = 600

/**
 * App theme. With [materialDynamic] on (Android 12+) the colours come from the system
 * wallpaper palette; otherwise, or on older Android, the fallback dark scheme is used.
 * Colour changes fade smoothly instead of switching at once.
 */
@Composable
fun DivyaTheme(
    materialDynamic: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val useDynamic = materialDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val target = remember(useDynamic, context) {
        if (useDynamic) dynamicDarkColorScheme(context) else FallbackDarkScheme
    }

    MaterialTheme(
        colorScheme = animatedScheme(target),
        content = content
    )
}

@Composable
private fun Color.animated(): Color =
    animateColorAsState(
        targetValue = this,
        animationSpec = tween(durationMillis = THEME_FADE_MS),
        label = "themeColor"
    ).value

/** Every colour role of [target], each one animated towards its new value. */
@Composable
private fun animatedScheme(target: ColorScheme): ColorScheme =
    target.copy(
        primary = target.primary.animated(),
        onPrimary = target.onPrimary.animated(),
        primaryContainer = target.primaryContainer.animated(),
        onPrimaryContainer = target.onPrimaryContainer.animated(),
        secondary = target.secondary.animated(),
        onSecondary = target.onSecondary.animated(),
        secondaryContainer = target.secondaryContainer.animated(),
        onSecondaryContainer = target.onSecondaryContainer.animated(),
        tertiary = target.tertiary.animated(),
        onTertiary = target.onTertiary.animated(),
        tertiaryContainer = target.tertiaryContainer.animated(),
        onTertiaryContainer = target.onTertiaryContainer.animated(),
        background = target.background.animated(),
        onBackground = target.onBackground.animated(),
        surface = target.surface.animated(),
        onSurface = target.onSurface.animated(),
        surfaceVariant = target.surfaceVariant.animated(),
        onSurfaceVariant = target.onSurfaceVariant.animated(),
        outline = target.outline.animated(),
        outlineVariant = target.outlineVariant.animated(),
        error = target.error.animated(),
        onError = target.onError.animated(),
        surfaceContainer = target.surfaceContainer.animated(),
        surfaceContainerLow = target.surfaceContainerLow.animated(),
        surfaceContainerHigh = target.surfaceContainerHigh.animated(),
        surfaceContainerHighest = target.surfaceContainerHighest.animated()
    )


// ----------------------------------------------------
// SHARED SURFACES
// ----------------------------------------------------

/** A standard Material 3 card: a tonal surface with the content inside a column. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    contentPadding: Dp = 16.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

/** A round Material 3 tonal icon button. */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(buttonSize),
        content = content
    )
}
