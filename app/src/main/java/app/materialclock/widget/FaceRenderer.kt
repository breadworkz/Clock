package app.materialclock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import app.materialclock.data.FaceFill
import app.materialclock.data.IndexSet
import app.materialclock.data.MinorIndex
import app.materialclock.data.NumeralLayout
import app.materialclock.data.NumeralSystem
import app.materialclock.data.WidgetConfig
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The still half of the clock: outline, fill, indices and numerals, drawn once into a bitmap.
 *
 * Everything that moves is somebody else's problem. The hands are the host's `AnalogClock` and
 * the date is its `TextClock`. This bitmap changes only when the configuration, the size, the
 * palette or the day/night mode changes, which is why the widget costs nothing to run.
 */
object FaceRenderer {

    class Colours(
        val face: Int,
        /** Numerals. */
        val onFace: Int,
        /** Dots, rings and ticks. A step down from [onFace] so the numerals still lead. */
        val minor: Int,
        val outline: Int,
        /** The single accent, for previews and thumbnails that draw one representative colour. */
        val accent: Int,
        val hourHand: Int,
        val minuteHand: Int,
        val secondHand: Int,
        val pin: Int,
    )

    /**
     * Draws the face at [w] × [h] pixels.
     *
     * Returns the bitmap along with the `Rmin` and centre the hands must be scaled against, so the
     * two renderers cannot disagree about where the middle of the clock is.
     */
    class Face(val bitmap: Bitmap, val rMin: Float, val cx: Float, val cy: Float)

    fun render(context: Context, config: WidgetConfig, colours: Colours, w: Int, h: Int): Face {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val wf = w.toFloat()
        val hf = h.toFloat()

        // The fitted path is a *centreline*, so an outline stroke would spill half its width past
        // the widget. Fitting into a rect inset by half the stroke is what keeps it inside.
        val strokeR = config.outline.widthR
        val provisional = facePath(config.shape, config.pillOrientation, config.fit, wf, hf)
        val provisionalOutline = FaceOutline(provisional, wf / 2f, hf / 2f)
        val strokePx = strokeR * provisionalOutline.rMin
        val path = if (strokePx > 0f) {
            facePath(
                config.shape, config.pillOrientation, config.fit,
                wf - strokePx, hf - strokePx,
            ).also { it.offset(strokePx / 2f, strokePx / 2f) }
        } else {
            provisional
        }

        val outline = FaceOutline(path, wf / 2f, hf / 2f)
        val r = outline.rMin

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        when (config.faceFill) {
            FaceFill.NONE -> Unit
            FaceFill.CONTAINER -> {
                fill.color = colours.face
                canvas.drawPath(path, fill)
            }
        }
        if (strokePx > 0f) {
            canvas.drawPath(
                path,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokePx
                    // Round joins rather than mitred: a mitre on a star's sharp vertex reaches four
                    // times the stroke width past the path and straight out of the widget.
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    color = colours.outline
                },
            )
        }

        drawIndices(context, canvas, config, colours, outline, path, r)
        drawNumerals(context, canvas, config, colours, outline, r)

        return Face(bitmap, r, wf / 2f, hf / 2f)
    }

    /** Dots or ticks at the hour positions the numerals do not occupy. */
    private fun drawIndices(
        context: Context,
        canvas: Canvas,
        config: WidgetConfig,
        colours: Colours,
        outline: FaceOutline,
        path: Path,
        r: Float,
    ) {
        if (config.minorIndices == MinorIndex.NONE) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colours.minor }
        val numbered = if (config.numerals == NumeralSystem.NONE) emptySet() else config.indexSet.hours

        when (config.minorIndices) {
            MinorIndex.DOTS, MinorIndex.RINGS -> {
                val filled = config.minorIndices == MinorIndex.DOTS
                paint.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
                paint.strokeWidth = 0.014f * r
                // Rings read lighter than dots at the same diameter, so they are drawn a shade
                // larger. Without that, swapping between the two looks like a size change.
                val radius = DialGeometry.MINOR_DOT_RADIUS * r * (if (filled) 1.0f else 1.35f)
                for (hour in 1..12) {
                    if (hour in numbered) continue
                    val at = ringPoint(outline, config, hour * 30f, r, DialGeometry.NUMERAL_RING)
                    canvas.drawCircle(at.first, at.second, radius, paint)
                }
            }
            MinorIndex.TICKS, MinorIndex.TICKS_60 -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                val count = if (config.minorIndices == MinorIndex.TICKS_60) 60 else 12
                for (i in 0 until count) {
                    val isHour = i % (count / 12) == 0
                    val hour = i / (count / 12) + if (i % (count / 12) == 0) 0 else 0
                    if (isHour && (hour.takeIf { it != 0 } ?: 12) in numbered) continue
                    val angle = i * 360f / count
                    val len = (if (isHour) DialGeometry.TICK_LENGTH else DialGeometry.TICK_LENGTH_MINOR) * r
                    paint.strokeWidth = (if (isHour) 0.014f else 0.008f) * r
                    val outer = ringPoint(outline, config, angle, r, 1f, inset = 0.04f * r)
                    val inner = ringPoint(outline, config, angle, r, 1f, inset = 0.04f * r + len)
                    canvas.drawLine(outer.first, outer.second, inner.first, inner.second, paint)
                }
            }
            MinorIndex.NONE -> Unit
        }
    }

    private fun drawNumerals(
        context: Context,
        canvas: Canvas,
        config: WidgetConfig,
        colours: Colours,
        outline: FaceOutline,
        r: Float,
    ) {
        if (config.numerals == NumeralSystem.NONE || config.indexSet == IndexSet.NONE) return

        val ringR = maxOf(
            DialGeometry.NUMERAL_RING,
            if (config.indexSet == IndexSet.ALL_TWELVE) DialGeometry.MIN_RING_FOR_TWELVE else 0f,
        )

        // Measure the widest label the system can produce at cap height 1, so the clamp is against
        // real glyphs rather than an assumption about how wide a numeral is.
        val probe = TextInk.numeralPaint(
            context, 100f,
            width = config.numeralWidth.axis,
            weight = config.numeralWeight.axis,
            roundness = config.numeralRound.axis,
            system = config.numerals,
        )
        val widest = TextInk.widest(config.numerals)
        val probeInk = TextInk.ink(probe, widest)
        val widestPerCap = if (probeInk.height() > 0) {
            probeInk.width().toFloat() / probeInk.height().toFloat()
        } else {
            1f
        }
        val capR = DialGeometry.clampedCap(config.numeralSize, config.indexSet, ringR, widestPerCap)
        val capPx = capR * r

        val paint = TextInk.numeralPaint(
            context, 100f,
            width = config.numeralWidth.axis,
            weight = config.numeralWeight.axis,
            roundness = config.numeralRound.axis,
            system = config.numerals,
        ).apply {
            color = colours.onFace
            textSize = TextInk.sizeForCap(this, widest, capPx)
        }
        val ink = Rect()

        for (hour in config.indexSet.hours) {
            val text = TextInk.label(hour, config.numerals)
            if (text.isEmpty()) continue
            TextInk.ink(paint, text, ink)
            val angleDeg = (hour % 12) * 30f
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

            val (x, y, rotation) = when (config.numeralLayout) {
                NumeralLayout.CIRCLE_UPRIGHT -> {
                    val rr = ringR * r
                    Triple(
                        outline.cx + sin(angleRad) * rr,
                        outline.cy - cos(angleRad) * rr,
                        0f,
                    )
                }
                NumeralLayout.SHAPE_UPRIGHT, NumeralLayout.SHAPE_ROTATED -> {
                    val margin = 0.10f * r
                    val hit = outline.placeGlyph(angleRad, ink, margin)
                    // Turned with the dial, but flipped through the lower half so a numeral is
                    // never upside down, which is the same rule the world-clock dial uses.
                    val rot = if (config.numeralLayout == NumeralLayout.SHAPE_ROTATED) {
                        angleDeg + if (angleDeg.mod(360f) in 90f..270f) 180f else 0f
                    } else {
                        0f
                    }
                    if (hit == null) {
                        Triple(outline.cx, outline.cy, 0f)
                    } else {
                        Triple(hit.x, hit.y, rot)
                    }
                }
            }

            canvas.save()
            if (rotation != 0f) canvas.rotate(rotation, x, y)
            // getTextBounds' rect is relative to the pen origin, so this centres the *ink* rather
            // than the advance box; the difference is visible on "1" and on Roman "I".
            canvas.drawText(
                text,
                x - ink.width() / 2f - ink.left,
                y + ink.height() / 2f - ink.bottom,
                paint,
            )
            canvas.restore()
        }
    }

    /** A point at [angleDeg] either on a true circle or on the outline itself. */
    private fun ringPoint(
        outline: FaceOutline,
        config: WidgetConfig,
        angleDeg: Float,
        r: Float,
        ringFraction: Float,
        inset: Float = 0f,
    ): Pair<Float, Float> {
        val a = Math.toRadians(angleDeg.toDouble()).toFloat()
        return when (config.numeralLayout) {
            NumeralLayout.CIRCLE_UPRIGHT -> {
                val rr = ringFraction * r - inset
                outline.cx + sin(a) * rr to outline.cy - cos(a) * rr
            }
            else -> {
                val hit = outline.castRay(a)
                if (hit == null) {
                    outline.cx to outline.cy
                } else {
                    hit.x + hit.nx * inset to hit.y + hit.ny * inset
                }
            }
        }
    }
}
