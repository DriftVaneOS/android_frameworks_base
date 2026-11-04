/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.driftvane.clocks

import android.content.Context
import android.graphics.Color
import android.icu.util.TimeZone
import android.view.View
import com.android.systemui.customization.clocks.view.DefaultClockFaceLayout
import com.android.systemui.plugins.keyguard.VRect
import com.android.systemui.plugins.keyguard.data.model.AlarmData
import com.android.systemui.plugins.keyguard.data.model.WeatherData
import com.android.systemui.plugins.keyguard.data.model.ZenData
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAnimations
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEventListeners
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPositionAnimationArgs
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.plugins.keyguard.ui.clocks.ThemeConfig
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import java.io.PrintWriter
import java.util.Locale

class DriftClockController(
    private val hostCtx: Context,
    private val pluginCtx: Context,
    private val settings: ClockSettings,
    private val clockId: String,
) : ClockController {

    override val smallClock = DriftFace(hostCtx, pluginCtx, settings, clockId, isLarge = false)
    override val largeClock = DriftFace(hostCtx, pluginCtx, settings, clockId, isLarge = true)

    override val config = ClockConfig(clockId, DriftClocks.name(pluginCtx, clockId), DriftClocks.desc(pluginCtx, clockId))

    override val eventListeners = ClockEventListeners()

    override val events = object : ClockEvents {
        override fun onTimeZoneChanged(timeZone: TimeZone) {
            val tz = java.util.TimeZone.getTimeZone(timeZone.id)
            smallClock.view.timeZone = tz
            largeClock.view.timeZone = tz
            tick()
        }
        override fun onTimeFormatChanged(formatKind: TimeFormatKind) = tick()
        override fun onLocaleChanged(locale: Locale) = tick()
        override fun onWeatherDataChanged(data: WeatherData) {}
        override fun onAlarmDataChanged(data: AlarmData) {}
        override fun onZenDataChanged(data: ZenData) {}
    }

    private fun tick() {
        smallClock.events.onTimeTick()
        largeClock.events.onTimeTick()
    }

    override fun initialize(isDarkTheme: Boolean, dozeFraction: Float, foldFraction: Float) {
        listOf(smallClock, largeClock).forEach {
            it.events.onThemeChanged(it.theme.copy(isDarkTheme = isDarkTheme))
            it.animations.doze(dozeFraction)
            it.events.onTimeTick()
        }
    }

    override fun dump(pw: PrintWriter) {
        pw.println("DriftClock $clockId style=${largeClock.view.style}")
    }
}

class DriftFace(
    private val hostCtx: Context,
    private val pluginCtx: Context,
    settings: ClockSettings,
    clockId: String,
    isLarge: Boolean,
) : ClockFaceController {

    override val config = ClockFaceConfig()
    override var theme = ThemeConfig(isDarkTheme = true, settings.seedColor)

    override val view = DriftClockView(pluginCtx, isLarge).apply {
        id = if (isLarge) ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE else ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL
        visibility = View.VISIBLE
        style = DriftClocks.styleFor(clockId, settings.axes)
    }

    override val layout = DefaultClockFaceLayout(view)

    private val clockIdRef = clockId

    private fun applyColors() {
        val accent = theme.getDefaultColor(pluginCtx)
        view.accentColor = accent
        view.mainColor = if (theme.isDarkTheme) Color.WHITE else 0xFF16141B.toInt()
    }

    override val events = object : ClockFaceEvents {
        override fun onTimeTick() = view.refreshTime()
        override fun onThemeChanged(theme: ThemeConfig) {
            this@DriftFace.theme = theme
            applyColors()
        }
        override fun onFontSettingChanged(fontSizePx: Float) { view.baseSizePx = fontSizePx }
        override fun onTargetRegionChanged(targetRegion: VRect) {}
        override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {}
    }

    override val animations = object : ClockAnimations {
        override fun enter() {
            view.scaleX = 0.92f; view.scaleY = 0.92f; view.alpha = 0f
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(420).start()
        }
        override fun doze(fraction: Float) { view.dozing = fraction }
        override fun fold(fraction: Float) {}
        override fun charge() {
            view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(180)
                .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(260).start() }.start()
        }
        override fun onPickerCarouselSwiping(swipingFraction: Float) {}
        override fun onPositionAnimated(args: ClockPositionAnimationArgs) {}
        override fun onFidgetTap(x: Float, y: Float) {
            // tap the clock: a little bounce
            view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90)
                .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(240).start() }.start()
        }
        override fun onFontAxesChanged(style: ClockAxisStyle) {
            view.style = DriftClocks.styleFor(clockIdRef, style)
        }
    }

    init { applyColors() }
}
