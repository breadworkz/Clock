package app.materialclock.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * A spring-wound timer's dial: the disc seen **edge-on**, wearing M3 Expressive's slider anatomy.
 *
 * ## The drum
 *
 * A stepped slider was the wrong shape for this. A pomodoro timer is a knurled wheel you turn, and
 * what sells a wheel on a flat screen is perspective: graduations sit at equal angles *around* a
 * cylinder and are then projected, `x = centre + R·sin θ`, which bunches them toward the rim the
 * way a real drum's do. Depth is `cos θ`, and it drives **width and brightness**: the graduation
 * under the handle is thickest and brightest, and the rest thin and dim until they cross the
 * silhouette at ±90°. Height stays constant across the whole face: shortening them with distance
 * as well was tried and read as a ruler with a curve drawn on it.
 *
 * ## Why it now looks like Material
 *
 * The first version was a bespoke drawing that happened to sit in a Material app. This one borrows
 * the expressive **slider's** anatomy, measured off the concept's own audio player. The drum body
 * *is* the track, in `surfaceContainerHighest`, at an 18 dp radius rather than a full capsule,
 * because a capsule reads as a pill and this is meant to read as a drum.
 *
 * There is no separate handle. The graduation passing under the centre *becomes* the marker and
 * hands the role back as it leaves, so nothing appears or disappears.
 *
 * There is deliberately no "wound so far" fill. It was tried and it read as a separate chip stuck
 * to the left end: a drum's position is shown by which graduation is under the handle, not by how
 * much of a track is coloured.
 *
 * ## Snap and smoothness
 *
 * The drag accumulates in a float and the drawing follows that float, not the rounded value. The
 * earlier version snapped, and the cause was its own re-sync: it reset `pos` from the incoming
 * `value` whenever the two drifted by more than three-quarters of a unit, which during a drag is
 * constantly. Every emitted step therefore yanked the drum back onto a whole number. Now an
 * incoming value this control just emitted is recognised as its own echo and ignored; only a
 * genuinely external change re-seats the drum.
 */
@Composable
fun Winder(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 76.dp,
    /**
     * Angle between adjacent graduations. At ~4.6° roughly forty are visible across the face,
     * which is what makes it read as knurling rather than as a ruler.
     */
    stepRadians: Float = 0.080f,
    majorEvery: Int = 5,
) {
    val snapSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val ridge = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val onChange by rememberUpdatedState(onValueChange)

    var pos by remember { mutableFloatStateOf(value.toFloat()) }
    // What this control last reported. An incoming `value` equal to it is our own echo.
    var emitted by remember { mutableIntStateOf(value) }
    if (value != emitted && abs(pos - value) > 0.5f) {
        pos = value.toFloat()
        emitted = value
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "Winder"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = range.first.toFloat()..range.last.toFloat(),
                    steps = range.last - range.first,
                )
            }
            .pointerInput(range) {
                detectHorizontalDragGestures(
                    // Snap. Let go between two graduations and the drum settles onto the nearer
                    // one rather than resting off-detent, which is what a real wound dial does
                    // and what makes the value under the marker trustworthy.
                    //
                    // Animated rather than assigned, on the expressive *fast spatial* spring:
                    // under-damped, so it overshoots a hair and comes back, the way a sprung
                    // mechanism would. Jumping the value would land in the right place and feel
                    // like nothing happened.
                    onDragEnd = {
                        val target = pos.roundToInt()
                            .coerceIn(range.first, range.last)
                            .toFloat()
                        scope.launch {
                            animate(initialValue = pos, targetValue = target, animationSpec = snapSpec) { v, _ ->
                                pos = v
                            }
                        }
                        if (pos.roundToInt() != emitted) {
                            emitted = pos.roundToInt()
                            onChange(emitted)
                        }
                    },
                ) { change, dx ->
                    change.consume()
                    // Radius comes from the drawn width, so a pixel of drag turns the drum by
                    // dx/R radians and finger and graduations travel together.
                    //
                    // Negated: the drum is a cylinder seen edge-on, so dragging *left* rolls its
                    // near face away from you and brings higher numbers up under the marker. That
                    // is the same direction a physical wound timer turns. Following the finger
                    // instead means pulling left counts down, which reads backwards on a dial.
                    val delta = -(dx / (size.width / 2f)) / stepRadians
                    pos = (pos + delta).coerceIn(range.first.toFloat(), range.last.toFloat())
                    val rounded = pos.roundToInt()
                    if (rounded != emitted) {
                        emitted = rounded
                        onChange(rounded)
                    }
                }
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width / 2f
        val trackH = size.height
        val corner = 18.dp.toPx()
        val ridgeLen = trackH * 0.68f

        // Squarer than a stock slider: a fully-rounded capsule reads as a pill, and this is a
        // drum. 18 dp keeps it in the M3 shape scale without pretending to be a capsule.
        drawRoundRect(
            color = track,
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(corner),
        )
        val halfTurn = (PI / 2 / stepRadians).toInt()
        // How near a graduation has to come before it starts turning into the marker. Wider than
        // one step, so two neighbours always share the transition and the handoff is continuous.
        val selectRange = stepRadians * 1.6f

        for (i in -halfTurn..halfTurn) {
            val unit = pos.roundToInt() + i
            val theta = (unit - pos) * stepRadians
            val depth = cos(theta)
            if (depth <= 0.02f) continue

            val x = cx + r * sin(theta)
            val inRange = unit >= range.first && unit <= range.last
            val major = unit % majorEvery == 0 && inRange

            // Selection, smoothstepped on angular distance from the centre. There is no separate
            // handle: the graduation passing under the middle *becomes* the marker (growing,
            // thickening and taking the accent colour) and hands the role to its neighbour on the
            // way out. The previous version drew a fixed bar and skipped any graduation near it,
            // which is why they blinked out of existence as they approached.
            val t = (1f - (abs(theta) / selectRange)).coerceIn(0f, 1f)
            val sel = t * t * (3f - 2f * t)

            val baseAlpha = ((if (major) 0.34f else 0.14f) + 0.66f * depth * depth) *
                (if (inRange) 1f else 0.22f)
            val h = ridgeLen * (1f + 0.22f * sel)
            val w = (6.4f * (0.26f + 0.74f * depth)) * (1f + 1.05f * sel)
            val color = lerp(
                (if (major) accent else ridge).copy(alpha = baseAlpha.coerceIn(0f, 1f)),
                accent,
                sel,
            )

            drawLine(
                color = color,
                start = Offset(x, cy - h / 2f),
                end = Offset(x, cy + h / 2f),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }
    }
}
