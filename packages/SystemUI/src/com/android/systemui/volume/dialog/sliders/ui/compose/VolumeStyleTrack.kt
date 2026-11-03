/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Volume slider styles for the Android 17 volume dialog.
 * Style designs (gradient, double layer, neumorph, outline, shaded layer)
 * based on RisingOS "Volume Styles" by minaripenguin.
 */

package com.android.systemui.volume.dialog.sliders.ui.compose

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.material3.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

/** Settings.System key, same name RisingOS uses. 0 = stock Android. */
const val VOLUME_STYLE_KEY = "custom_volume_styles"

const val VOLUME_STYLE_STOCK = 0
const val VOLUME_STYLE_DOUBLE_LAYER = 1
const val VOLUME_STYLE_GRADIENT = 2
const val VOLUME_STYLE_NEUMORPH = 3
const val VOLUME_STYLE_NEUMORPH_OUTLINE = 4
const val VOLUME_STYLE_OUTLINE = 5
const val VOLUME_STYLE_SHADED_LAYER = 6

@Composable
fun rememberVolumeStyle(): Int {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var style by remember {
        mutableIntStateOf(
            Settings.System.getIntForUser(
                resolver, VOLUME_STYLE_KEY, VOLUME_STYLE_STOCK, UserHandle.USER_CURRENT
            )
        )
    }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    style =
                        Settings.System.getIntForUser(
                            resolver,
                            VOLUME_STYLE_KEY,
                            VOLUME_STYLE_STOCK,
                            UserHandle.USER_CURRENT,
                        )
                }
            }
        resolver.registerContentObserver(
            Settings.System.getUriFor(VOLUME_STYLE_KEY),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return style
}

/**
 * Draws the whole track (inactive + active part) in one of the custom styles.
 * [fraction] is the filled amount, 0..1. Vertical tracks fill from the bottom.
 */
@Composable
fun StyledVolumeTrack(
    style: Int,
    fraction: Float,
    colors: SliderColors,
    isEnabled: Boolean,
    isVertical: Boolean,
    isRtl: Boolean,
    cornerSize: Dp,
    modifier: Modifier = Modifier,
) {
    val active = if (isEnabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = if (isEnabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    val f = fraction.coerceIn(0f, 1f)

    Canvas(modifier) {
        val r = CornerRadius(cornerSize.toPx(), cornerSize.toPx())
        val w = size.width
        val h = size.height

        // Rect of the filled part.
        val (fillTopLeft, fillSize) =
            when {
                isVertical -> Offset(0f, h * (1 - f)) to Size(w, h * f)
                isRtl -> Offset(w * (1 - f), 0f) to Size(w * f, h)
                else -> Offset.Zero to Size(w * f, h)
            }

        fun DrawScope.filled(brush: Brush, inset: Float = 0f) {
            if (f <= 0f) return
            drawRoundRect(
                brush = brush,
                topLeft = fillTopLeft + Offset(inset, inset),
                size = Size(
                    (fillSize.width - inset * 2).coerceAtLeast(0f),
                    (fillSize.height - inset * 2).coerceAtLeast(0f),
                ),
                cornerRadius = r,
            )
        }

        val stroke = 2f * density
        when (style) {
            VOLUME_STYLE_DOUBLE_LAYER -> {
                drawRoundRect(inactive, cornerRadius = r)
                // Translucent outer layer, solid inner layer.
                filled(SolidColor(active.copy(alpha = 0.45f)))
                filled(SolidColor(active), inset = 4f * density)
            }
            VOLUME_STYLE_GRADIENT -> {
                drawRoundRect(inactive, cornerRadius = r)
                val start = if (isVertical) Offset(0f, h) else Offset(if (isRtl) w else 0f, 0f)
                val end = if (isVertical) Offset(0f, 0f) else Offset(if (isRtl) 0f else w, 0f)
                filled(
                    Brush.linearGradient(
                        listOf(active.copy(alpha = 0.35f), active),
                        start = start,
                        end = end,
                    )
                )
            }
            VOLUME_STYLE_NEUMORPH, VOLUME_STYLE_NEUMORPH_OUTLINE -> {
                // Soft shadow below-right, soft light above-left.
                val o = 3f * density
                drawRoundRect(Color.Black.copy(alpha = 0.25f), topLeft = Offset(o, o), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(-o, -o), cornerRadius = r)
                drawRoundRect(inactive, cornerRadius = r)
                if (style == VOLUME_STYLE_NEUMORPH_OUTLINE) {
                    drawRoundRect(active, cornerRadius = r, style = Stroke(stroke))
                    filled(SolidColor(active), inset = 5f * density)
                } else {
                    filled(SolidColor(active))
                }
            }
            VOLUME_STYLE_OUTLINE -> {
                drawRoundRect(
                    active.copy(alpha = 0.8f),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(w - stroke, h - stroke),
                    cornerRadius = r,
                    style = Stroke(stroke),
                )
                filled(SolidColor(active), inset = 5f * density)
            }
            VOLUME_STYLE_SHADED_LAYER -> {
                drawRoundRect(inactive, cornerRadius = r)
                filled(SolidColor(active))
                // Lighter band at the leading edge of the fill.
                if (f > 0f) {
                    val band = 24f * density
                    clipRect(
                        left = fillTopLeft.x,
                        top = fillTopLeft.y,
                        right = fillTopLeft.x + fillSize.width,
                        bottom = fillTopLeft.y + fillSize.height,
                    ) {
                        val bandTopLeft =
                            when {
                                isVertical -> fillTopLeft
                                isRtl -> fillTopLeft
                                else -> Offset(fillTopLeft.x + fillSize.width - band, 0f)
                            }
                        val bandSize = if (isVertical) Size(w, band) else Size(band, h)
                        drawRoundRect(
                            Color.White.copy(alpha = 0.22f),
                            topLeft = bandTopLeft,
                            size = bandSize,
                            cornerRadius = r,
                        )
                    }
                }
            }
            else -> {
                drawRoundRect(inactive, cornerRadius = r)
                filled(SolidColor(active))
            }
        }
    }
}
