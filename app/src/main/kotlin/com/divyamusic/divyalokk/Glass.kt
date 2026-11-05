package com.divyamusic.divyalokk

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


// ----------------------------------------------------
// COLOURS AND THEME
// ----------------------------------------------------

val AccentPink = Color(0xFFFF2D55)
val AccentCoral = Color(0xFFFF9F6B)
val AccentViolet = Color(0xFF5E60CE)

private val DivyaColors = darkColorScheme(
    primary = AccentPink,
    onPrimary = Color.White,
    secondary = AccentCoral,
    onSecondary = Color.White,
    background = Color(0xFF0B0B14),
    onBackground = Color.White,
    surface = Color(0xFF15151F),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFC9C9D6)
)

/** The three colours behind the liquid glow. [primary] is also the main accent. */
data class GlowPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

val DefaultGlowPalette = GlowPalette(AccentPink, AccentCoral, AccentViolet)

val LocalGlowPalette = staticCompositionLocalOf { DefaultGlowPalette }

/** False turns off the frosted glass surfaces and the animated glow (flat solid surfaces). */
val LocalLiquidGlass = staticCompositionLocalOf { true }

/** Main accent for sliders, selected rows and the play button. */
@Composable
@ReadOnlyComposable
fun divyaAccent(): Color = LocalGlowPalette.current.primary

/**
 * App theme. With [materialDynamic] on (Android 12+) the colour scheme comes from the
 * system wallpaper palette. A non-null [artworkPalette] (liquid glass dynamic) replaces
 * the glow and accent colours with the colours of the current song's artwork.
 * [liquidGlass] = false draws flat solid surfaces instead of the glass look.
 */
@Composable
fun DivyaTheme(
    materialDynamic: Boolean = false,
    artworkPalette: GlowPalette? = null,
    solidColor: Color? = null,
    liquidGlass: Boolean = true,
    content: @Composable () -> Unit
) {
    val canUseDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val scheme = if (materialDynamic && canUseDynamic) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        DivyaColors
    }

    val palette = when {
        // Solid colour: every glow, accent and button uses this one colour
        solidColor != null -> GlowPalette(solidColor, solidColor, solidColor)
        artworkPalette != null -> artworkPalette
        materialDynamic && canUseDynamic -> GlowPalette(scheme.primary, scheme.secondary, scheme.tertiary)
        else -> DefaultGlowPalette
    }

    CompositionLocalProvider(
        LocalGlowPalette provides palette,
        LocalLiquidGlass provides liquidGlass
    ) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}


// ----------------------------------------------------
// LIQUID BACKGROUND
// ----------------------------------------------------

@Composable
fun LiquidBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Colours glide to the new palette when the song changes
    val palette = LocalGlowPalette.current
    val primary by animateColorAsState(palette.primary, tween(durationMillis = 700), label = "glowPrimary")
    val secondary by animateColorAsState(palette.secondary, tween(durationMillis = 700), label = "glowSecondary")
    val tertiary by animateColorAsState(palette.tertiary, tween(durationMillis = 700), label = "glowTertiary")

    val glassOn = LocalLiquidGlass.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (glassOn) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1B0B22),
                                Color(0xFF0B0B14),
                                Color(0xFF071521)
                            )
                        )
                    )
                } else {
                    // Flat background when liquid glass is off
                    Modifier.background(Color(0xFF0B0B14))
                }
            )
    ) {
        if (glassOn) {
            GlowBlob(size = 320.dp, x = (-90).dp, y = (-60).dp, color = primary.copy(alpha = 0.55f))
            GlowBlob(size = 280.dp, x = 200.dp, y = 320.dp, color = secondary.copy(alpha = 0.35f))
            GlowBlob(size = 240.dp, x = (-60).dp, y = 620.dp, color = tertiary.copy(alpha = 0.4f))
        }
        content()
    }
}

@Composable
private fun GlowBlob(size: Dp, x: Dp, y: Dp, color: Color) {
    val radiusPx = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        modifier = Modifier
            .offset(x, y)
            .size(size)
            .background(
                Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(radiusPx, radiusPx),
                    radius = radiusPx
                ),
                CircleShape
            )
    )
}


// ----------------------------------------------------
// GLASS SURFACES
// ----------------------------------------------------

/**
 * Frosted-glass look: a translucent tinted fill that fades downwards, plus a
 * bright rim that is stronger at the top-left, like light catching an edge.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = Color.White,
    strength: Float = 0.12f
): Modifier = composed {

    if (!LocalLiquidGlass.current) {

        // Liquid glass off: a flat surface, tinted a little by [tint]
        return@composed this
            .clip(shape)
            .background(lerp(Color(0xFF15151F), tint, (strength * 2f).coerceIn(0f, 1f)))
    }

    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = strength * 2f),
                    tint.copy(alpha = strength * 0.6f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.6f),
                    Color.White.copy(alpha = 0.05f)
                )
            ),
            shape = shape
        )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.White,
    strength: Float = 0.12f,
    contentPadding: Dp = 16.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .glass(shape, tint, strength)
            .padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 44.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .glass(CircleShape, strength = 0.14f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}
