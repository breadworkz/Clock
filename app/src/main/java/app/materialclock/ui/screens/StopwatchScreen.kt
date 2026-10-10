package app.materialclock.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import app.materialclock.core.Lap
import app.materialclock.core.Stopwatch
import app.materialclock.core.stopwatchParts

/**
 * The stopwatch.
 *
 * Extrapolated: the concept set contains no stopwatch at all, so this reuses the timer's own
 * vocabulary (the stacked hollow digits, the wide pills) rather than importing a different look.
 * The bottom pair of digits is hundredths, which is why the whole screen is driven off a
 * frame-paced ticker instead of a one-second one.
 *
 * The fastest and slowest laps are marked in tertiary and error. That is not in the renders; it is
 * the one addition, on the grounds that a lap list nobody can compare is just a receipt.
 */
@Composable
fun StopwatchScreen(
    stopwatch: Stopwatch,
    nowElapsedMillis: Long,
    onToggle: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val elapsed = stopwatch.elapsed(nowElapsedMillis)
    val (a, b, c) = elapsed.stopwatchParts()
    val fastest = stopwatch.fastest
    val slowest = stopwatch.slowest

    // Laps are prepended, and a keyed LazyColumn holds its scroll against the item it was already
    // showing. Without this, every new lap is added *above* the viewport and the list silently
    // keeps displaying the oldest three. Ride it back to the top instead.
    val listState = rememberLazyListState()
    LaunchedEffect(stopwatch.laps.size) {
        if (stopwatch.laps.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Spacer(Modifier.height(12.dp))

        StackedDigits(
            parts = listOf(a, b, c),
            capHeight = READOUT_CAP,
            color = MaterialTheme.colorScheme.primary,
            leading = Icons.Rounded.Timer,
            label = "Stopwatch, $a minutes $b seconds $c hundredths" +
                if (stopwatch.running) ", running" else ", stopped",
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // The same 35 dp that separates mm from ss inside the readout. The readout's rows are
        // ink-tight boxes, so this is a true ink gap and the button sits on the stack's own rhythm
        // rather than at some other distance that happens to look close.
        Spacer(Modifier.height(STACK_GAP))

        // The control row is one height, always, so the band left for laps is the same on every
        // frame of the screen's life: before the first press, mid-run, and after a reset. Nothing
        // below the readout ever moves because of what is above it.
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            StopwatchControls(
                idle = !stopwatch.running && elapsed.isZero,
                running = stopwatch.running,
                onToggle = onToggle,
                onLap = onLap,
                onReset = onReset,
            )
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(LAP_GAP),
            ) {
                items(stopwatch.laps, key = { it.index }) { lap ->
                    LapRow(lap, fastest == lap.index, slowest == lap.index)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * The controls: **one row, one height, two states, and a morph between them.**
 *
 * Untouched, the screen offers only Start. Lap and Reset have nothing to act on yet, and a live
 * control that does nothing is worse than an absent one. It is sized to its own label, because a
 * lone button that spans the screen claims an importance the row does not have once it splits.
 *
 * Pressing it runs two phases, in order, and the order is the effect:
 *
 *  1. **Expand.** The pill grows from its content width to the full row on the expressive spatial
 *     spring. Its shape is already fully round, so growing the width *is* the morph; a small pill
 *     becomes a wide one with no corner interpolation to get wrong.
 *  2. **Split.** Only once it has arrived does the three-button row cross-fade in, with its 6 dp
 *     gaps opening from nothing. So the wide pill is seen to divide rather than to be replaced.
 *
 * The delay on phase 2 is what sequences them; running both on one curve reads as a dissolve.
 *
 * Both states are exactly [ROW_HEIGHT], and that is the whole point. An earlier version graded the
 * button heights down as laps accumulated to buy list space, which worked and looked wrong: the
 * controls twitched under the thumb on every lap press, and the band left for laps changed four
 * times. Fixing the height instead means nothing below the readout ever moves, and it makes the lap
 * band a constant, the same on a fresh screen as on a full one.
 */
@Composable
private fun StopwatchControls(
    idle: Boolean,
    running: Boolean,
    onToggle: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
) {
    val expanded = !idle
    // The *effects* spring, not the spatial one, and not by accident. Material's fast spatial
    // spring is under-damped (0.6/800), which means it takes about 240 ms and rings past its
    // target. Both properties are wrong here: the split is timed to land the moment the pill
    // arrives, so the pill has to actually arrive rather than wobble around full width.
    // `fastEffectsSpec` is critically damped and settles in well under half the time.
    val expand by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "expand",
    )
    // The label goes almost at once. A word sliding along inside a container that is still growing
    // reads as a glitch, so it is gone before the eye can follow it. The container it leaves
    // behind is what the three buttons then divide.
    val label by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        animationSpec = tween(durationMillis = LABEL_FADE_MS),
        label = "label",
    )
    // Deliberately not a spring: this one has to *wait*, and a delay is a tween's parameter. It
    // only waits on the way out, since collapsing back to Start should not stall on an empty row.
    val split by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = SPLIT_MS,
            delayMillis = if (expanded) EXPAND_LEAD_MS else 0,
        ),
        label = "split",
    )
    // The spring overshoots past 1 on the way in, which is wanted for the width but would push the
    // interpolation out of range.
    val e = expand.coerceIn(0f, 1f)

    val startStop: @Composable (Boolean) -> Unit = { compact ->
        WidePill(
            text = if (running) "Stop" else "Start",
            icon = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            onClick = onToggle,
            container = if (running) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            content = if (running) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            height = ROW_HEIGHT,
            compact = compact,
        )
    }

    SubcomposeLayout(
        Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(horizontal = 20.dp),
    ) { constraints ->
        val full = constraints.maxWidth
        val h = ROW_HEIGHT.roundToPx()

        // The resting width is **one readout row**: the `: ss` line directly above it, colon cell
        // and both digits. Measured off the real composable rather than added up from the cell
        // constant and a guess at the digit advance, so the two stay locked together if the cap
        // height or the width axis ever changes.
        val inset = ROW_INK_INSET.roundToPx()
        val rowWidth = subcompose("rowProbe") {
            DigitRow(
                part = "00",
                capHeight = READOUT_CAP,
                color = MaterialTheme.colorScheme.primary,
                separator = true,
            )
        }.first().measure(Constraints()).width

        // …but never narrower than the label needs. The two figures are within a few dp of each
        // other today; if a translation ever made "Start" the longer of the two, clipping the word
        // would be the worse failure.
        val natural = subcompose("probe") {
            WidePill(
                text = "Start",
                icon = Icons.Rounded.PlayArrow,
                onClick = {},
                height = ROW_HEIGHT,
                compact = true,
                fillWidth = false,
                contentPaddingH = IDLE_PADDING_H,
            )
        }.first().measure(Constraints(minHeight = h, maxHeight = h)).width

        // A row's box begins `ROW_INK_INSET` left of its colon, so matching the box makes the
        // button look off-centre: right edge flush with the digits, left edge hanging past the
        // dots. Drop the inset and anchor the **right** edge to the row instead. That is the edge
        // the eye actually checks, and it stays correct if the label clamp below widens the button
        // leftwards.
        val collapsedW = maxOf(rowWidth - inset, natural).coerceAtMost(full)
        val collapsedX = ((full - rowWidth) / 2 + rowWidth - collapsedW).coerceAtLeast(0)

        val w = lerp(collapsedW, full, e)
        val x = lerp(collapsedX, 0, e)

        val single = if (split < 1f) {
            subcompose("single") {
                Box(Modifier.graphicsLayer { alpha = 1f - split }) {
                    // Always the *idle* button, never "Stop". `running` has already flipped by the
                    // time this frame draws, and relabelling the pill at the instant of the tap
                    // makes the thing that grows a different button from the one that was pressed.
                    // The new labels belong to the split, which is what the split is for.
                    WidePill(
                        text = "Start",
                        icon = Icons.Rounded.PlayArrow,
                        onClick = onToggle,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        height = ROW_HEIGHT,
                        compact = true,
                        contentPaddingH = IDLE_PADDING_H,
                        contentAlpha = label,
                    )
                }
            }.first().measure(Constraints.fixed(w, h))
        } else {
            null
        }

        val trio = if (split > 0f) {
            subcompose("trio") {
                Box(Modifier.graphicsLayer { alpha = split }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP * split)) {
                        Box(Modifier.weight(1f)) {
                            WidePill(text = "Lap", onClick = onLap, height = ROW_HEIGHT, compact = true)
                        }
                        Box(Modifier.weight(1f)) {
                            WidePill(
                                text = "Reset",
                                onClick = onReset,
                                outlined = true,
                                content = MaterialTheme.colorScheme.onSurface,
                                height = ROW_HEIGHT,
                                compact = true,
                            )
                        }
                        Box(Modifier.weight(1f)) { startStop(true) }
                    }
                }
            }.first().measure(Constraints.fixed(full, h))
        } else {
            null
        }

        layout(full, h) {
            single?.place(x, 0)
            trio?.place(0, 0)
        }
    }
}

/** How long the pill has to finish growing before the row is allowed to divide. */
private const val EXPAND_LEAD_MS = 95
private const val SPLIT_MS = 150
private const val LABEL_FADE_MS = 70

/** Roomier than the three-abreast pills: this one is not fighting two neighbours for width. */
private val IDLE_PADDING_H = 28.dp

/** `StackedDigits` sets its rows 35 dp apart; the button sits on the same rhythm. */
private val STACK_GAP = 35.dp

/** Shared so the Start button can be measured against a row drawn at the same size. */
private val READOUT_CAP = 66.dp

private val GAP = 6.dp
private val ROW_HEIGHT = 56.dp

/**
 * Lap rows are deliberately shorter than a list item usually is. Every dp here buys list, and the
 * row carries three short strings (an index badge, a split and a total), none of which needs the
 * height a two-line item would. At [LAP_ROW_HEIGHT] plus [LAP_GAP] a lap costs 52 dp, so the
 * four-lap control layout shows three of them where the old 124 dp cap showed one.
 */
private val LAP_ROW_HEIGHT = 48.dp
private val LAP_GAP = 4.dp

@Composable
private fun LapRow(lap: Lap, isFastest: Boolean, isSlowest: Boolean) {
    val badge = when {
        isFastest -> MaterialTheme.colorScheme.tertiaryContainer
        isSlowest -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val onBadge = when {
        isFastest -> MaterialTheme.colorScheme.onTertiaryContainer
        isSlowest -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val (m, s, cs) = lap.split.stopwatchParts()
    val (tm, ts, tcs) = lap.total.stopwatchParts()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(percent = 50),
        modifier = Modifier.fillMaxWidth().height(LAP_ROW_HEIGHT),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = badge, shape = CircleShape, modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        lap.index.toString(),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = onBadge,
                    )
                }
            }
            Text(
                "$m:$s.$cs",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$tm:$ts.$tcs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
