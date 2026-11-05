/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.driftvane.clocks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.View
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Every knob a DriftVane clock has. Values come from the Wallpaper & style sliders. */
data class DriftStyle(
    val weight: Float = 700f,      // 100..1000
    val width: Float = 100f,       // 25..151 (percent)
    val height: Float = 100f,      // 100..200 (percent, vertical stretch)
    val slant: Float = 0f,         // 0..12 (degrees forward)
    val twoTone: Boolean = false,
    val outline: Boolean = false,
    val stacked: Boolean = false,
    val glass: Boolean = false,
    val align: Float = 0f,         // 0 = left, 1 = center, 2 = right
    val date: Boolean = false,
    val line: Boolean = false,
)

/**
 * Draws the time with Canvas so any combination of weight / width / height / slant / two-tone /
 * outline / stacked / glass / alignment / date / drift line works, at any size.
 */
class DriftClockView(context: Context, private val isLarge: Boolean) : View(context) {

    var style = DriftStyle()
        set(v) { field = v; applyFont(); requestLayout(); invalidate() }

    var baseSizePx = 200f
        set(v) { field = v; requestLayout(); invalidate() }

    var mainColor = Color.WHITE
        set(v) { field = v; invalidate() }
    var accentColor = Color.WHITE
        set(v) { field = v; invalidate() }

    var dozing = 0f
        set(v) { field = v; invalidate() }

    var timeZone: TimeZone = TimeZone.getDefault()

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x22FFFFFF
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private var hours = "12"
    private var minutes = "00"
    private var dateText = ""
    private var variableFont = false

    init {
        applyFont()
        refreshTime()
    }

    private fun applyFont() {
        // Roboto Flex has weight + width axes; fall back to scaling when it isn't there.
        val flex = File("/system/fonts/RobotoFlex-Regular.ttf")
        val base = if (flex.exists()) runCatching { Typeface.createFromFile(flex) }.getOrNull() else null
        timePaint.typeface = base ?: Typeface.create("sans-serif", Typeface.NORMAL)
        val w = style.weight.coerceIn(100f, 1000f)
        val wd = style.width.coerceIn(25f, 151f)
        variableFont = base != null && timePaint.setFontVariationSettings("'wght' $w, 'wdth' $wd")
        if (!variableFont) {
            timePaint.typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), w.toInt(), false)
        }
        datePaint.typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 700, false)
    }

    fun refreshTime() {
        val cal = Calendar.getInstance(timeZone)
        val h24 = DateFormat.is24HourFormat(context)
        val h = cal.get(if (h24) Calendar.HOUR_OF_DAY else Calendar.HOUR).let { if (!h24 && it == 0) 12 else it }
        hours = if (h24) "%02d".format(h) else h.toString()
        minutes = "%02d".format(cal.get(Calendar.MINUTE))
        dateText = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply { timeZone = this@DriftClockView.timeZone }
            .format(Date()).uppercase()
        contentDescription = "$hours:$minutes"
        requestLayout()
        invalidate()
    }

    private val stacked get() = isLarge && style.stacked
    private val showDate get() = isLarge && style.date
    private val showGlass get() = isLarge && style.glass && dozing < 0.5f
    private val showLine get() = isLarge && style.line

    /** Shrinks the clock so it always fits the space it's given (no clipping, no overlap). */
    private var fit = 1f

    private fun textSize(): Float = baseSizePx * fit
    private fun vScale(): Float = style.height.coerceIn(100f, 200f) / 100f
    private fun hScale(): Float = if (variableFont) 1f else style.width.coerceIn(25f, 151f) / 100f
    private fun pad(): Float = if (showGlass) textSize() * 0.22f else 0f

    private fun lines(): List<Pair<String, Boolean>> =
        if (stacked) listOf(hours to true, minutes to false)
        else listOf("$hours:$minutes" to true)

    private fun lineHeight(): Float = textSize() * 0.80f * vScale()

    private fun neededSize(): Pair<Float, Float> {
        timePaint.textSize = textSize()
        timePaint.textScaleX = hScale()
        // a little extra for the slant and the glyph overhang so nothing gets cut off
        val textW = lines().maxOf { timePaint.measureText(it.first) } * 1.04f + textSize() * 0.25f * style.slant / 12f
        var h = lineHeight() * lines().size + (if (stacked) textSize() * 0.04f else 0f)
        if (showDate) h += textSize() * 0.32f
        if (showLine) h += textSize() * 0.16f
        val p = pad()
        return (textW + p * 2) to (h + p * 2)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        fit = 1f
        val (needW, needH) = neededSize()
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val screen = resources.displayMetrics
        val availW = if (wMode == MeasureSpec.UNSPECIFIED) screen.widthPixels * 0.9f
            else MeasureSpec.getSize(widthMeasureSpec).toFloat()
        // the big clock may use about a third of the screen height, never more
        var maxH = if (isLarge) screen.heightPixels * 0.30f else Float.MAX_VALUE
        if (hMode != MeasureSpec.UNSPECIFIED) maxH = minOf(maxH, MeasureSpec.getSize(heightMeasureSpec).toFloat())
        fit = minOf(1f, availW * 0.96f / needW, maxH / needH).coerceAtLeast(0.2f)
        val (w, h) = neededSize()
        setMeasuredDimension(resolveSize(w.toInt() + 2, widthMeasureSpec), resolveSize(h.toInt() + 2, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val ts = textSize()
        val p = pad()
        val ink = blend(mainColor, Color.WHITE, dozing)
        val accent = blend(accentColor, Color.WHITE, dozing)

        if (showGlass) {
            val r = ts * 0.28f
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            cardPaint.shader = LinearGradient(width.toFloat(), 0f, 0f, height.toFloat(),
                0xB8262330.toInt(), 0xB80B0A0D.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, r, r, cardPaint)
            borderPaint.strokeWidth = ts * 0.008f + 1f
            canvas.drawRoundRect(rect, r, r, borderPaint)
        }

        timePaint.textSize = ts
        timePaint.textScaleX = hScale()
        timePaint.textSkewX = -style.slant.coerceIn(0f, 12f) / 40f
        val outline = style.outline || dozing > 0.5f
        timePaint.style = if (outline) Paint.Style.STROKE else Paint.Style.FILL
        timePaint.strokeWidth = ts * 0.025f

        val inner = width - p * 2
        var y = p
        lines().forEachIndexed { i, (text, isHours) ->
            val tw = timePaint.measureText(text)
            val x = p + when {
                style.align >= 1.5f -> inner - tw
                style.align >= 0.5f -> (inner - tw) / 2f
                else -> 0f
            }
            timePaint.color = if (style.twoTone && (isHours || !stacked)) accent else ink
            canvas.save()
            // baseline sits at the bottom of the line box; stretch vertically around it
            val baseline = y + lineHeight()
            canvas.scale(1f, vScale(), x, baseline)
            if (!stacked && style.twoTone) {
                // "12:" in accent, "34" in the main color
                val head = "$hours:"
                canvas.drawText(head, x, baseline, timePaint)
                timePaint.color = ink
                canvas.drawText(minutes, x + timePaint.measureText(head), baseline, timePaint)
            } else {
                canvas.drawText(text, x, baseline, timePaint)
            }
            canvas.restore()
            y += lineHeight() + if (stacked && i == 0) ts * 0.04f else 0f
        }

        if (showLine) {
            linePaint.color = accent
            linePaint.strokeWidth = ts * 0.06f
            linePaint.style = Paint.Style.STROKE
            val path = Path()
            val left = p + ts * 0.05f
            val right = width - p - ts * 0.05f
            val mid = y + ts * 0.08f
            path.moveTo(left, mid + ts * 0.03f)
            path.cubicTo(left + (right - left) * 0.3f, mid - ts * 0.06f,
                left + (right - left) * 0.6f, mid + ts * 0.08f, right, mid - ts * 0.03f)
            canvas.drawPath(path, linePaint)
            y += ts * 0.16f
        }

        if (showDate) {
            datePaint.textSize = ts * 0.16f
            datePaint.letterSpacing = 0.12f
            datePaint.color = accent
            val dw = datePaint.measureText(dateText)
            val x = p + when {
                style.align >= 1.5f -> inner - dw
                style.align >= 0.5f -> (inner - dw) / 2f
                else -> ts * 0.03f
            }
            canvas.drawText(dateText, x, y + ts * 0.24f, datePaint)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
        )
    }
}
