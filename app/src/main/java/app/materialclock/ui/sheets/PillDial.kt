package app.materialclock.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePickerSelectionMode
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.materialclock.ui.theme.ClockFace
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A clock dial shaped like a pill, whose hour marks follow the pill's edge instead of a circle.
 *
 * ## Why this is hand-drawn
 *
 * Material's dial cannot be reshaped. `TimePickerShapes` exposes only `timeFieldShape` and
 * `periodSelectorShape`; the circle is drawn inside `ClockFace`, and both `ClockFace` and the
 * `AnalogTimePickerState` it needs are Kotlin-`internal`, visible in the bytecode but unusable
 * from outside the module. Even reachable, they would still lay the numerals out on a circle. So
 * the geometry is solved here.
 *
 * ## The geometry
 *
 * The pill is a stadium: every point within [r] of the horizontal segment from `(−c, 0)` to
 * `(c, 0)`, which is a `(w − h) × h` rectangle capped by two half-discs. For a ray leaving the
 * centre at angle θ, [spoke] finds where it exits, in closed form and with no iteration:
 *
 *  - it crosses a **flat** edge at `t = r / |dy|`, and that is the true exit only while the x it
 *    reaches is still over the rectangle, `|x| ≤ c`;
 *  - otherwise it leaves through a **cap**, from `|p − (±c, 0)| = r`, giving
 *    `t = |dx|·c + √(dx²c² − c² + r²)`. The discriminant is `r² − c²·dy²`, and `r > c` for any
 *    pill, so it is never negative and there is no degenerate case to guard.
 *
 * The numeral is then pulled in by a constant margin along the **inward normal**. That is the part
 * worth getting right, because the normal points in different directions on the two sections:
 * vertically on the flats, and radially back toward the cap's own centre on the ends. Both land on
 * the offset stadium (same `c`, radius `r − margin`), so every numeral sits at the identical
 * perpendicular distance from the edge. Insetting along the ray instead, which is the obvious
 * shortcut, leaves the numerals near 2 and 4 visibly closer to the boundary than the one at 12.
 *
 * Because the exit distance varies with direction, so does the hand: it is drawn to the numeral
 * rather than to a fixed radius, and is longest pointing at 3 and 9.
 */
@Composable
fun PillDial(
    state: TimePickerState,
    modifier: Modifier = Modifier,
    onHourPicked: () -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val dialColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    val minutes = state.selection == TimePickerSelectionMode.Minute
    // 24-hour mode needs a second ring, exactly as Material's circular dial does: 0–11 outside,
    // 12–23 inside. In 12-hour mode there is only ever one.
    val rings = if (!minutes && state.is24hour) 2 else 1

    val labelStyle = remember(density) {
        ClockFace.numerals(capHeight = 11.dp, width = 105f, weight = 500, tabular = false)
    }

    val label = if (minutes) "minutes" else "hour"
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PILL_ASPECT)
            .semantics {
                contentDescription = "Pick the $label. Currently " +
                    if (minutes) "${state.minute} minutes" else "${state.hour} o'clock"
            }
            .pointerInput(minutes, rings) {
                detectTapGestures { p -> apply(state, p, size.width.toFloat(), size.height.toFloat(), rings, minutes, onHourPicked) }
            }
            .pointerInput(minutes, rings) {
                detectDragGestures(
                    onDragEnd = { if (!minutes) onHourPicked() },
                ) { change, _ ->
                    apply(state, change.position, size.width.toFloat(), size.height.toFloat(), rings, minutes, null)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = dialColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f),
            size = Size(w, h),
        )

        val margin = MARGIN_FRACTION * h
        val selectedAngle = selectedAngle(state, minutes)
        val selectedRing = if (rings == 2 && state.hour !in 1..12) 1 else 0
        // The tip is solved with the *selected* label's own support, so the hand ends on the
        // numeral rather than near it; the two must use the same inset or they drift apart.
        val selectedText = if (minutes) "%02d".format(state.minute) else currentValue(state, minutes).toString()
        val tip = place(
            angle = selectedAngle,
            w = w, h = h,
            margin = margin + selectedRing * ringStep(h),
            half = measurer.measure(selectedText, labelStyle).size,
        )

        // Hand first, so the puck and the numeral sit on top of its end.
        drawLine(
            color = accent,
            start = Offset(w / 2f, h / 2f),
            end = Offset(tip.x, tip.y),
            strokeWidth = 2.dp.toPx(),
        )
        drawCircle(accent, radius = 3.dp.toPx(), center = Offset(w / 2f, h / 2f))
        drawCircle(accent, radius = PUCK_DP.dp.toPx(), center = Offset(tip.x, tip.y))

        for (ring in 0 until rings) {
            val inset = margin + ring * ringStep(h)
            for (i in 0 until 12) {
                val value = valueAt(i, ring, minutes, state.is24hour)
                val angle = i * PI.toFloat() / 6f
                val text = if (minutes) "%02d".format(value) else value.toString()
                val laid = measurer.measure(text, labelStyle)
                val at = place(angle, w, h, inset, laid.size)
                val isSelected = value == currentValue(state, minutes)
                drawText(
                    textLayoutResult = laid,
                    color = if (isSelected) onAccent else ink,
                    topLeft = Offset(at.x - laid.size.width / 2f, at.y - laid.size.height / 2f),
                )
            }
        }
    }
}

/** 360 × 184 in the prototype, which is the proportion the pill was drawn at. */
private const val PILL_ASPECT = 360f / 184f
/** Ink clearance, not anchor distance. See [place]. 20 px on the prototype's 184 px pill. */
private const val MARGIN_FRACTION = 20f / 184f
private const val PUCK_DP = 18f

/** How far the inner ring of a 24-hour dial sits inside the outer one. */
private fun ringStep(h: Float) = 0.20f * h

private class Spoke(val x: Float, val y: Float, val nx: Float, val ny: Float)

/**
 * A numeral's centre: on the offset stadium, and then pushed in far enough that its *ink* clears
 * the wall by the same amount as every other numeral's.
 *
 * Insetting the anchor by a constant is not the same as insetting the glyph by a constant, because
 * the glyph's box is axis-aligned while the normal at the caps is oblique, and because "10" is
 * twice as wide as "3". Measured on the prototype, that left "10" 17.1 px from the wall against
 * "3"'s 21.9 px: a 4.8 px difference, visible as the ends of the pill looking tighter than the top.
 *
 * The correction is the box's own support along the normal, `(w/2)|nx| + (h/2)|ny|`, which is the
 * distance from the centre to the far side of an axis-aligned rectangle in that direction. Adding
 * it makes the *ink* clearance the constant, which is the one the eye is actually reading.
 */
private fun place(
    angle: Float,
    w: Float,
    h: Float,
    margin: Float,
    half: androidx.compose.ui.unit.IntSize,
): Spoke {
    val edge = spoke(angle, w, h, 0f)
    val support = (half.width / 2f) * abs(edge.nx) + (half.height / 2f) * abs(edge.ny)
    val d = margin + support
    return Spoke(edge.x + edge.nx * d, edge.y + edge.ny * d, edge.nx, edge.ny)
}

/**
 * Where the ray at [angle] meets the stadium, pulled back along the inward normal by [margin].
 * [angle] is measured clockwise from 12 o'clock. See the class docs for the derivation.
 */
private fun spoke(angle: Float, w: Float, h: Float, margin: Float): Spoke {
    val r = h / 2f
    val c = (w - h) / 2f
    val cx = w / 2f
    val cy = h / 2f
    val dx = sin(angle)
    val dy = -cos(angle)

    val flat = if (abs(dy) > 1e-6f) r / abs(dy) else Float.MAX_VALUE
    return if (flat * abs(dx) <= c) {
        // Out through a flat edge: the inward normal is straight up or down.
        val nx = 0f
        val ny = -sign(dy)
        Spoke(cx + flat * dx, cy + flat * dy + ny * margin, nx, ny)
    } else {
        // Out through a cap: the inward normal runs back to that cap's own centre.
        val s = if (dx >= 0f) 1f else -1f
        val b = abs(dx) * c
        val t = b + sqrt(b * b - c * c + r * r)
        val ux = (t * dx - s * c) / r
        val uy = (t * dy) / r
        val bx = cx + s * c + ux * r
        val by = cy + uy * r
        Spoke(bx - ux * margin, by - uy * margin, -ux, -uy)
    }
}

private fun currentValue(state: TimePickerState, minutes: Boolean): Int = when {
    minutes -> state.minute
    state.is24hour -> state.hour
    else -> (state.hour % 12).takeIf { it != 0 } ?: 12
}

private fun valueAt(index: Int, ring: Int, minutes: Boolean, is24: Boolean): Int = when {
    minutes -> index * 5
    is24 -> if (ring == 0) (index.takeIf { it != 0 } ?: 12) else (index + 12) % 24
    else -> index.takeIf { it != 0 } ?: 12
}

private fun selectedAngle(state: TimePickerState, minutes: Boolean): Float =
    if (minutes) {
        state.minute * PI.toFloat() / 30f
    } else {
        (state.hour % 12) * PI.toFloat() / 6f
    }

/**
 * Turns a touch into a value.
 *
 * The angle is taken from the centre, so a press anywhere along a spoke picks that spoke. That is
 * what makes the ends of the pill, where the numerals are furthest out, no harder to hit than the
 * top. Hours snap to the twelve marks; minutes snap to the minute, not to the five, because the
 * library's dial does the same and rounding a drag to the nearest five feels broken.
 */
private fun apply(
    state: TimePickerState,
    at: Offset,
    w: Float,
    h: Float,
    rings: Int,
    minutes: Boolean,
    onHourPicked: (() -> Unit)?,
) {
    val dx = at.x - w / 2f
    val dy = at.y - h / 2f
    if (hypot(dx, dy) < 1f) return
    // atan2(dx, -dy) puts 0 at twelve o'clock and grows clockwise, which is how a clock is read.
    var turns = atan2(dx, -dy) / (2f * PI.toFloat())
    if (turns < 0f) turns += 1f

    if (minutes) {
        state.minute = (turns * 60f).roundToInt() % 60
    } else {
        val h12 = (turns * 12f).roundToInt() % 12
        state.hour = if (rings == 2 && isInnerRing(at, w, h)) (h12 + 12) % 24 else h12
        onHourPicked?.invoke()
    }
}

/** Only asked in 24-hour mode: is the press nearer the inner ring than the outer one? */
private fun isInnerRing(at: Offset, w: Float, h: Float): Boolean {
    val angle = atan2(at.x - w / 2f, -(at.y - h / 2f))
    val outer = spoke(angle, w, h, MARGIN_FRACTION * h + 8f)
    val inner = spoke(angle, w, h, MARGIN_FRACTION * h + 8f + ringStep(h))
    val dOuter = hypot(at.x - outer.x, at.y - outer.y)
    val dInner = hypot(at.x - inner.x, at.y - inner.y)
    return dInner < dOuter
}

