/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.driftvane.clocks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import com.android.internal.annotations.Keep
import com.android.systemui.plugins.annotations.Requires
import com.android.systemui.plugins.keyguard.ui.clocks.AxisType
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFontAxis
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFontAxis.Companion.merge
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMessageBuffers
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMetadata
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPickerConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockProviderPlugin
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings

/** Clock ids, defaults and the sliders/toggles each one gets in Wallpaper & style. */
object DriftClocks {
    const val TALL = "DRIFTVANE_TALL"
    const val GLASS = "DRIFTVANE_GLASS"
    const val FLOW = "DRIFTVANE_FLOW"
    val ALL = listOf(TALL, GLASS, FLOW)

    fun defaults(id: String): DriftStyle = when (id) {
        TALL -> DriftStyle(weight = 650f, width = 40f, height = 130f, stacked = true, twoTone = true)
        GLASS -> DriftStyle(weight = 800f, width = 100f, height = 100f, glass = true, date = true, align = 1f)
        else -> DriftStyle(weight = 900f, width = 120f, height = 100f, line = true, date = true)
    }

    fun styleFor(id: String, axes: ClockAxisStyle): DriftStyle {
        val d = defaults(id)
        fun f(k: String, def: Float) = axes[k] ?: def
        fun b(k: String, def: Boolean) = (axes[k] ?: if (def) 1f else 0f) >= 0.5f
        return DriftStyle(
            weight = f("wght", d.weight),
            width = f("wdth", d.width),
            height = f("tall", d.height),
            slant = f("slnt", d.slant),
            twoTone = b("twotone", d.twoTone),
            outline = b("outline", d.outline),
            stacked = b("stack", d.stacked),
            glass = b("glass", d.glass),
            align = f("align", d.align),
            date = b("date", d.date),
            line = b("line", d.line),
        )
    }

    fun axes(ctx: Context, id: String): List<ClockFontAxis> {
        val d = defaults(id)
        fun s(r: Int) = ctx.getString(r)
        fun bool(v: Boolean) = if (v) 1f else 0f
        return listOf(
            ClockFontAxis("wght", AxisType.Float, 1000f, 100f, d.weight, s(R.string.ax_weight), s(R.string.ax_weight_desc)),
            ClockFontAxis("wdth", AxisType.Float, 151f, 25f, d.width, s(R.string.ax_width), s(R.string.ax_width_desc)),
            ClockFontAxis("tall", AxisType.Float, 200f, 100f, d.height, s(R.string.ax_height), s(R.string.ax_height_desc)),
            ClockFontAxis("slnt", AxisType.Float, 12f, 0f, d.slant, s(R.string.ax_slant), s(R.string.ax_slant_desc)),
            ClockFontAxis("align", AxisType.Float, 2f, 0f, d.align, s(R.string.ax_align), s(R.string.ax_align_desc)),
            ClockFontAxis("stack", AxisType.Boolean, 1f, 0f, bool(d.stacked), s(R.string.ax_stack), s(R.string.ax_stack_desc)),
            ClockFontAxis("twotone", AxisType.Boolean, 1f, 0f, bool(d.twoTone), s(R.string.ax_twotone), s(R.string.ax_twotone_desc)),
            ClockFontAxis("outline", AxisType.Boolean, 1f, 0f, bool(d.outline), s(R.string.ax_outline), s(R.string.ax_outline_desc)),
            ClockFontAxis("glass", AxisType.Boolean, 1f, 0f, bool(d.glass), s(R.string.ax_glass), s(R.string.ax_glass_desc)),
            ClockFontAxis("date", AxisType.Boolean, 1f, 0f, bool(d.date), s(R.string.ax_date), s(R.string.ax_date_desc)),
            ClockFontAxis("line", AxisType.Boolean, 1f, 0f, bool(d.line), s(R.string.ax_line), s(R.string.ax_line_desc)),
        )
    }

    fun name(ctx: Context, id: String) = ctx.getString(when (id) {
        TALL -> R.string.dv_tall_name
        GLASS -> R.string.dv_glass_name
        else -> R.string.dv_flow_name
    })

    fun desc(ctx: Context, id: String) = ctx.getString(when (id) {
        TALL -> R.string.dv_tall_desc
        GLASS -> R.string.dv_glass_desc
        else -> R.string.dv_flow_desc
    })
}

@Keep // entry point SystemUI loads; must not be stripped
@Requires(target = ClockProviderPlugin::class, version = ClockProviderPlugin.VERSION)
class DriftClockProvider : ClockProviderPlugin {
    private lateinit var hostCtx: Context
    private lateinit var pluginCtx: Context

    override fun onCreate(hostCtx: Context, pluginCtx: Context) {
        this.hostCtx = hostCtx
        this.pluginCtx = pluginCtx
    }

    override fun initialize(buffers: ClockMessageBuffers?) {}

    override fun getClocks(): List<ClockMetadata> = DriftClocks.ALL.map { ClockMetadata(it) }

    override fun createClock(ctx: Context, settings: ClockSettings): ClockController {
        val id = settings.clockId ?: DriftClocks.TALL
        require(id in DriftClocks.ALL) { "$id unsupported by DriftVane clocks" }
        return DriftClockController(ctx, pluginCtx, settings, id)
    }

    override fun getClockPickerConfig(settings: ClockSettings): ClockPickerConfig {
        val id = settings.clockId ?: DriftClocks.TALL
        require(id in DriftClocks.ALL) { "$id unsupported by DriftVane clocks" }
        return ClockPickerConfig(
            id,
            DriftClocks.name(pluginCtx, id),
            DriftClocks.desc(pluginCtx, id),
            thumbnail(id),
            isReactiveToTone = true,
            axes = DriftClocks.axes(pluginCtx, id).merge(settings.axes),
            presetConfig = null,
        )
    }

    /** Thumbnail = the clock itself, drawn small. */
    private fun thumbnail(id: String): BitmapDrawable {
        val v = DriftClockView(pluginCtx, isLarge = true)
        v.style = DriftClocks.defaults(id)
        v.mainColor = Color.WHITE
        v.accentColor = pluginCtx.getColor(android.R.color.system_accent1_200)
        v.baseSizePx = 120f
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        v.measure(spec, spec)
        val w = v.measuredWidth.coerceAtLeast(1)
        val h = v.measuredHeight.coerceAtLeast(1)
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return BitmapDrawable(pluginCtx.resources, bmp)
    }
}
