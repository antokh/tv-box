/**
 * Auramusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.auramusic.app.ui.tv

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * The TV look shared by every screen: dark glass over a blurred copy of whatever artwork is
 * in focus, white focus rings, and frosted "pills" for controls. Matches the tv-home launcher.
 */
object TvStyle {
    /** Behind everything. Kept a hair off pure black so the glass reads as glass. */
    val Base = Color(0xFF0B0B0E)
    val GlassFill = Color.Black.copy(alpha = 0.38f)
    val GlassStroke = Color.White.copy(alpha = 0.16f)
    val GlassSelected = Color.White.copy(alpha = 0.22f)
    val Focus = Color.White
    /** Active toggles (shuffle, repeat, liked, video) and the song that is playing. */
    val Accent = Color(0xFFFF3D5A)
    val OnFocus = Color(0xFF111111)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.62f)
    val TextTertiary = Color.White.copy(alpha = 0.42f)

    const val FocusScale = 1.08f
    val CardCorner = 14.dp
}

/** Artwork currently driving the full-screen backdrop: the focused item, else what is playing. */
object TvAmbient {
    var focusedArt by mutableStateOf<String?>(null)
}

/**
 * Full-screen backdrop: the artwork blurred and darkened, crossfading as focus moves.
 * Blur needs Android 12+; older sets get the same picture, just darker.
 */
@Composable
fun TvAmbientBackdrop(artUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(TvStyle.Base)) {
        Crossfade(targetState = artUrl, animationSpec = tween(700), label = "ambientArt") { url ->
            if (url != null) {
                val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Oversize so the blurred edges never show the base colour.
                            scaleX = 1.2f
                            scaleY = 1.2f
                            alpha = if (canBlur) 0.85f else 0.35f
                        }
                        .let { if (canBlur) it.blur(80.dp) else it },
                )
            }
        }
        // Scrim: darker at the top (clock, pills) and bottom (shelves), lighter in the middle.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.70f),
                        0.35f to Color.Black.copy(alpha = 0.35f),
                        0.65f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.90f),
                    ),
                ),
        )
    }
}

/** White ring + lift for a focused surface; nothing when unfocused. */
fun Modifier.tvFocusRing(focused: Boolean, shape: Shape): Modifier =
    this.border(width = if (focused) 3.dp else 0.dp, color = if (focused) TvStyle.Focus else Color.Transparent, shape = shape)

/** Scale animation used by every focusable surface. */
@Composable
fun tvFocusScale(focused: Boolean, to: Float = TvStyle.FocusScale): Float {
    val s by animateFloatAsState(targetValue = if (focused) to else 1f, animationSpec = tween(220), label = "tvFocusScale")
    return s
}
