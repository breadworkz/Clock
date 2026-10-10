package app.materialclock.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.materialclock.core.ClockTimer
import app.materialclock.core.TimerState
import app.materialclock.ui.theme.ClockFace
import app.materialclock.ui.theme.Numerals
import java.time.Duration

/**
 * The timer, in two states.
 *
 * **Setting** shows the entered time on one line, a ten-digit keypad, and a winder below it. The
 * winder is a drum seen edge-on rather than a slider; see [Winder] for why a slider was the wrong
 * shape for it. It and the keypad drive the same value, so you can type an exact 90 seconds or
 * wind roughly to twenty minutes, whichever the moment calls for.
 *
 * **Running** is a wavy circular ring with the remaining time inside it, and two controls: pause
 * and +10 s. `CircularWavyProgressIndicator` is genuinely expressive rather than a restyled
 * baseline ring, and the wave is the one thing on the screen in motion.
 *
 * A null timer *is* the setting state. There is no mode flag to fall out of step with the UI.
 */
private const val WIND_MAX_MINUTES = 60
private const val RING_SIZE_DP = 300

@Composable
fun TimersScreen(
    timer: ClockTimer?,
    draft: Duration,
    nowElapsedMillis: Long,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onWind: (Int) -> Unit,
    onStart: () -> Unit,
    onPauseResume: () -> Unit,
    onAddTen: () -> Unit,
    onCancel: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = timer != null,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "timer-state",
        modifier = modifier.fillMaxSize().padding(contentPadding),
    ) { running ->
        if (running && timer != null) {
            RunningTimer(timer, nowElapsedMillis, onPauseResume, onAddTen, onCancel)
        } else {
            SetTimer(draft, onDigit, onBackspace, onWind, onStart)
        }
    }
}

/* ── Setting ───────────────────────────────────────────────────────────────────────────────── */

@Composable
private fun SetTimer(
    draft: Duration,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onWind: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val total = draft.seconds
    val hh = "%02d".format(total / 3600)
    val mm = "%02d".format((total % 3600) / 60)
    val ss = "%02d".format(total % 60)
    val armed = total > 0

    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(4.dp))
            DraftReadout(hh, mm, ss)
            Spacer(Modifier.height(22.dp))
            Keypad(onDigit = onDigit, onBackspace = onBackspace)
            Spacer(Modifier.height(10.dp))
            // Below the keypad and above Start: you type an exact value or wind to a rough one, and
            // the winder sits next to the button it feeds.
            Winder(
                value = (total / 60).toInt().coerceAtMost(WIND_MAX_MINUTES),
                range = 0..WIND_MAX_MINUTES,
                onValueChange = onWind,
            )
            Spacer(Modifier.height(10.dp))
            WidePill(
                text = "Start",
                icon = Icons.Rounded.PlayArrow,
                onClick = onStart,
                height = 76.dp,
                container = if (armed) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                content = if (armed) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * `00:25:00` on one line, with the digits not yet reached dimmed.
 *
 * Two runs rather than a per-character colour, because each numeral run is one ink-tight box.
 * Dimming the lead-in is what makes the keypad feel like it is filling from the right rather than
 * replacing a whole field.
 */
@Composable
private fun DraftReadout(hh: String, mm: String, ss: String) {
    val text = "$hh:$mm:$ss"
    val firstReal = text.indexOfFirst { it in '1'..'9' }.let { if (it < 0) text.length - 1 else it }
    val cap = 56.dp
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$hh hours, $mm minutes, $ss seconds"
        },
    ) {
        if (firstReal > 0) {
            Numerals(
                text = text.take(firstReal),
                capHeight = cap,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                width = ClockFace.TIMER_WIDTH,
                weight = ClockFace.TIMER_WEIGHT,
                slashedZero = true,
            )
        }
        Numerals(
            text = text.drop(firstReal),
            capHeight = cap,
            color = MaterialTheme.colorScheme.onSurface,
            width = ClockFace.TIMER_WIDTH,
            weight = ClockFace.TIMER_WEIGHT,
            slashedZero = true,
        )
    }
}

/** Ten digits and a backspace on a 3 × 4 grid. */
@Composable
private fun Keypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c -> DigitKey(c, onDigit, Modifier.weight(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            DigitKey('0', onDigit, Modifier.weight(1f))
            KeyBox(
                modifier = Modifier.weight(1f),
                onClick = onBackspace,
                container = Color.Transparent,
                label = "Delete",
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, onDigit: (Char) -> Unit, modifier: Modifier = Modifier) {
    KeyBox(modifier = modifier, onClick = { onDigit(digit) }, label = digit.toString()) {
        Text(
            digit.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = ClockFace.family(opticalSize = 28f, width = 100f, weight = 500),
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun KeyBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    label: String,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = container,
        shape = CircleShape,
        modifier = modifier
            .height(58.dp)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/* ── Running ───────────────────────────────────────────────────────────────────────────────── */

@Composable
private fun RunningTimer(
    timer: ClockTimer,
    nowElapsedMillis: Long,
    onPauseResume: () -> Unit,
    onAddTen: () -> Unit,
    onCancel: () -> Unit,
) {
    val remaining = timer.remaining(nowElapsedMillis)
    val secs = remaining.seconds
    val text = if (secs >= 3600) {
        "%d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)
    } else {
        "%02d:%02d".format((secs % 3600) / 60, secs % 60)
    }
    val running = timer.state == TimerState.RUNNING

    // The readout is fitted, not fixed. "25:00" and "1:05:00" are different widths and the second
    // one ran straight out of the ring: a timer set past an hour is exactly when you would not be
    // watching it closely, so it must not be the case that breaks. Measured against the chord
    // available inside the stroke, then scaled down only if it does not fit, so short times keep
    // the full 62 dp.
    val measurer = rememberTextMeasurer()
    val maxTextWidth = RING_SIZE_DP.dp * 0.62f
    val capHeight = with(LocalDensity.current) {
        val ref = 62.dp
        val refStyle = ClockFace.numerals(
            capHeight = ref,
            width = ClockFace.TIMER_WIDTH,
            weight = ClockFace.TIMER_WEIGHT,
            slashedZero = true,
        )
        val measured = measurer.measure(text, refStyle).size.width.toDp()
        if (measured <= maxTextWidth) ref else ref * (maxTextWidth / measured)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { timer.fractionLeft(nowElapsedMillis) },
                modifier = Modifier.size(RING_SIZE_DP.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                stroke = Stroke(width = with(LocalDensity.current) { 18.dp.toPx() }, cap = StrokeCap.Round),
                trackStroke = Stroke(width = with(LocalDensity.current) { 18.dp.toPx() }, cap = StrokeCap.Round),
                gapSize = 10.dp,
                // Retuned for the size it is actually drawn at. The defaults come from
                // CircularProgressIndicatorTokens, which describe a **40 dp** indicator: 15 dp
                // wavelength, 1.6 dp amplitude, 4 dp stroke. Rendered at 300 dp with those, the
                // circumference fits about 63 waves and the amplitude is under a percent of the
                // radius, which is why it read as a fuzzy edge rather than a wave. At 76 dp the
                // ring carries about twelve, each big enough to see.
                wavelength = 76.dp,
                amplitude = { if (running) 1f else 0f },
                // The one thing on the screen that moves. A countdown that is visibly running is
                // the entire point of the state, and a still ring made it look paused.
                waveSpeed = if (running) 28.dp else 0.dp,
            )
            Numerals(
                text = text,
                capHeight = capHeight,
                color = MaterialTheme.colorScheme.onSurface,
                width = ClockFace.TIMER_WIDTH,
                weight = ClockFace.TIMER_WEIGHT,
                slashedZero = true,
            )
        }
        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                WidePill(
                    text = if (running) "Pause" else "Resume",
                    icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = onPauseResume,
                    height = 78.dp,
                )
            }
            Box(Modifier.weight(1f)) {
                WidePill(
                    text = "+10s",
                    icon = Icons.Rounded.Add,
                    onClick = onAddTen,
                    height = 78.dp,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        WidePill(
            text = "Cancel",
            icon = Icons.Rounded.Close,
            onClick = onCancel,
            outlined = true,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            height = 78.dp,
        )
        Spacer(Modifier.height(10.dp))
    }
}
