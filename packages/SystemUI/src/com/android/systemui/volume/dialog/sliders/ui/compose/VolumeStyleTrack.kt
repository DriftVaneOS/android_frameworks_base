/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Volume slider styles for the Android 17 volume dialog.
 * Ported from RisingOS "Volume Styles" by minaripenguin (Apache-2.0):
 * same layers, insets and Monet colors, redrawn for the Compose slider.
 */

package com.android.systemui.volume.dialog.sliders.ui.compose

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
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
 * Draws the whole track (inactive + active part) in one of the RisingOS styles.
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
    val context = LocalContext.current
    val night =
        (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    fun sys(id: Int) = Color(context.getColor(id))
    // RisingOS volumestyles_colors.xml (values / values-night)
    val blue = sys(if (night) android.R.color.system_accent1_500 else android.R.color.system_accent1_400)
    val green = sys(if (night) android.R.color.system_accent3_200 else android.R.color.system_accent3_600)
    val track =
        sys(if (night) android.R.color.system_neutral2_900 else android.R.color.system_accent1_600)
            .copy(alpha = 0.8f)
    val hollow = sys(if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10)
    val f = fraction.coerceIn(0f, 1f)
    val dim = if (isEnabled) 1f else 0.4f

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = cornerSize.toPx()
        val (fillTL, fillSz) =
            when {
                isVertical -> Offset(0f, h * (1 - f)) to Size(w, h * f)
                isRtl -> Offset(w * (1 - f), 0f) to Size(w * f, h)
                else -> Offset.Zero to Size(w * f, h)
            }
        // Gradient along the fill (start of the slider -> current level).
        val along =
            Brush.linearGradient(
                listOf(blue, green),
                start = if (isVertical) Offset(0f, h) else Offset(if (isRtl) w else 0f, 0f),
                end = if (isVertical) Offset(0f, fillTL.y) else Offset(if (isRtl) fillTL.x else fillSz.width, 0f),
            )
        // Gradient across the track (the RisingOS 90/270 degree layers).
        fun across(a: Color, b: Color) =
            if (isVertical) Brush.horizontalGradient(listOf(a, b)) else Brush.verticalGradient(listOf(a, b))

        fun layer(brush: Brush, insetDp: Float = 0f) {
            if (f <= 0f) return
            val ins = insetDp * density
            val sz = Size((fillSz.width - ins * 2).coerceAtLeast(0f), (fillSz.height - ins * 2).coerceAtLeast(0f))
            if (sz.width <= 0f || sz.height <= 0f) return
            val rr = (r - ins).coerceAtLeast(2f * density)
            drawRoundRect(brush, fillTL + Offset(ins, ins), sz, CornerRadius(rr, rr), alpha = dim)
        }
        fun knob() {
            if (f <= 0f) return
            val d = (if (isVertical) w else h)
            val c =
                when {
                    isVertical -> Offset(w / 2, fillTL.y + d / 2)
                    isRtl -> Offset(fillTL.x + d / 2, h / 2)
                    else -> Offset(fillSz.width - d / 2, h / 2)
                }
            drawCircle(Brush.linearGradient(listOf(blue, green)), radius = d / 2 - 6f * density, center = c, alpha = dim)
        }

        // Inactive track: volume_bg_color tinted volume_shape_color, alpha 0.8
        drawRoundRect(track, cornerRadius = CornerRadius(r, r))

        when (style) {
            VOLUME_STYLE_GRADIENT -> layer(along)
            VOLUME_STYLE_DOUBLE_LAYER -> {
                layer(along)
                layer(SolidColor(Color(0x40000000)))
                layer(along, 4f)
            }
            VOLUME_STYLE_NEUMORPH -> {
                layer(across(green, blue))
                layer(across(blue, green), 4f)
            }
            VOLUME_STYLE_NEUMORPH_OUTLINE -> {
                layer(along)
                layer(across(blue, green), 2f)
                layer(across(green, blue), 4f)
                knob()
            }
            VOLUME_STYLE_OUTLINE -> {
                layer(along)
                layer(SolidColor(hollow), 4f)
                knob()
            }
            VOLUME_STYLE_SHADED_LAYER -> {
                layer(along)
                layer(
                    if (isVertical) Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0x40000000)))
                    else Brush.horizontalGradient(
                        if (isRtl) listOf(Color.Transparent, Color.Transparent, Color(0x40000000))
                        else listOf(Color(0x40000000), Color.Transparent, Color.Transparent)
                    ),
                    4f,
                )
            }
            else -> layer(SolidColor(if (isEnabled) colors.activeTrackColor else colors.disabledActiveTrackColor))
        }
    }
}
