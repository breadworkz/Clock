package app.materialclock.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.materialclock.core.Alarm
import app.materialclock.data.WeekStart
import app.materialclock.data.order
import app.materialclock.ui.sheets.systemFirstDay
import app.materialclock.ui.theme.CapText
import app.materialclock.ui.theme.ClockFace
import app.materialclock.ui.theme.Numerals
import app.materialclock.ui.theme.inkBounds
import app.materialclock.ui.theme.rememberInkBounds
import app.materialclock.ui.theme.StretchedCaps
import java.time.DayOfWeek

/**
 * The alarm grid.
 *
 * Proportions are measured off the research-concept renders in `clock-mockups/reference/concept/`.
 * At 412 dp wide the render gives a tile of 184 × 250 dp on a 15.7 dp margin with a 12 dp gutter,
 * a 23.9 dp corner radius, and an hour numeral whose cap height is 153 dp: **0.62 of the tile**.
 * That figure is the design; it is what makes the grid read as a wall of numerals rather than a
 * list of cards with big text on them.
 *
 * Armed and disarmed differ by **weight, not by fill**: `wght` 260 against `wght` 120 on the same
 * ultra-condensed cut, solved from the render's measured stem-over-cap of 0.0639 and 0.0306. The
 * off state is a light numeral, not an outlined one.
 *
 * Every element of the time (hour, minutes, meridiem) carries the *same* tracking. See
 * [ClockFace.CONDENSED_TRACKING] for why that is one constant rather than solved per element.
 */
/**
 * The inset every edge of the tile shares: numeral ink, day letters, switch track alike.
 *
 * The render measured this twice and got the same answer both times: numeral ink 17.9 dp down a
 * 250 dp tile, and the switch's visible track 18.2 dp up from its bottom. It was a *fraction* of
 * the tile height while the tile had a fixed aspect; now the height is derived from the content it
 * has to be an absolute, or the two would chase each other.
 */
private const val CONTENT_INSET_DP = 18.1f

/**
 * From the top of the day row's box to the bottom of the switch you can *see*.
 *
 * `Switch` is 48 dp of touch target around a 32 dp track, centred, so the visible track starts 8 dp
 * down. The column's bottom padding gives the other 8 dp back, which is what lands the track on
 * [CONTENT_INSET_DP].
 */
private const val SWITCH_ROW_VISIBLE_DP = 40f
/** Measured: the minutes are three-quarters of the hour's cap height, cap-tops aligned. */
private const val MINUTE_CAP_FRACTION = 0.743f
/** Measured: the meridiem's cap is 0.145 of the hour's. */
private const val MERIDIEM_FRACTION = 0.145f
/** Derived, so the column closes on the hour's baseline: 1 − 0.743 − 0.145. */
private const val MERIDIEM_GAP_FRACTION = 1f - MINUTE_CAP_FRACTION - MERIDIEM_FRACTION
/**
 * The gap between the hour's and the minutes' boxes, as a fraction of the hour's cap.
 *
 * A fraction rather than the 8.3 dp it was measured at, because the cap is now solved per tile: a
 * fixed gap would be a different proportion of a 140 dp numeral than of a 170 dp one, and the fit
 * would drift between tiles.
 */
/**
 * What one step of [ClockFace.TRACKING_DELTA] is worth, measured in hour-caps.
 *
 * Tracking is in em and this gap is in caps, so the two only trade through the digit's own
 * ink-to-em ratio: a font size of `cap / DIGIT_EM` means `delta` em is `delta / DIGIT_EM` of a cap.
 * There are exactly two internal digit gaps in a time, one inside the hour and one inside the
 * minutes, so twice this comes out of the gap between them and the block's width is unchanged.
 */
private const val TRACKING_DELTA_AS_CAP = ClockFace.TRACKING_DELTA / ClockFace.DIGIT_EM
private const val GAP_FRACTION = 8.3f / 155f - 2f * TRACKING_DELTA_AS_CAP
/**
 * `Switch` reserves a 48 dp touch target around a 32 dp track, so its visible top is 8 dp below the
 * row's. Every vertical measurement here is of the switch you can *see*, so the row is placed 8 dp
 * higher than the number says.
 */
private const val SWITCH_TOUCH_INSET_DP = 8f
/** A little shorter than the switch's 32 dp track, because level with it reads as shouting. */
private const val DAY_CAP_DP = 23f
/** Fixed, so the block is one width whatever the repeat pattern and the switch never shifts. */
private const val DAY_BLOCK_DP = 91f
/** The constant slice of space between letters. The scale is derived from it, not tuned. */
private const val DAY_TRACKING_DP = 2.2f

@Composable
fun AlarmsScreen(
    alarms: List<Alarm>,
    weekStart: WeekStart,
    onToggle: (Long) -> Unit,
    onEdit: (Alarm) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // The setting reaches all the way into the tile, not only into the editor. An app that starts
    // the week on Monday in the picker and on Sunday in the grid is worse than one with no setting.
    val order = remember(weekStart) { weekStart.order(systemFirstDay()) }
    if (alarms.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ":( You currently dont have any alarms set try creating one",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(alarms, key = { it.id }) { alarm ->
                AlarmTile(alarm, order, onToggle = { onToggle(alarm.id) }, onEdit = { onEdit(alarm) })
            }
        }
    }
}

/**
 * The one cap height every tile in the grid is set at.
 *
 * Everything here is linear in the cap. The numerals are ink-tight, the gap is a fraction of the
 * cap and the meridiem is a fraction of the cap, so one measurement at a reference size gives the
 * constant of proportionality and the answer is a division.
 *
 * ## Why this takes no arguments about *this* tile
 *
 * It used to. It solved the cap per tile from that tile's own **ink**, and returned a per-tile
 * shift that centred that ink. Both were wrong, and together they were the whole bug:
 *
 *  - **Ink varies with the digits.** At `wdth` 25 a four-digit time spans 0.645 em (`11:15`) to
 *    0.820 em (`06:40`) in proportional figures. On a 174 dp tile that is a 45 dp swing in the
 *    block's width.
 *  - **The ceiling always won.** Solved over all 720 times, the width fit *never* came in under
 *    the old `0.62 * tileHeight` ceiling. The cap was therefore always the ceiling, the fit never
 *    bound, and every one of those 45 dp went into slack instead of into the numeral.
 *  - **The shift then split that slack.** `06:40` filled the tile and sat on a 12.7 dp margin;
 *    `07:15` was 35 dp narrower and sat on a 22.5 dp one. Two tiles side by side, ten dp apart,
 *    for no reason a reader could see.
 *
 * The fix is upstream of all three: with tabular figures every time is the same number of identical
 * cells, so there is exactly one answer and it is the same for every tile. This function computes
 * it from `"00"` at [ClockFace.WEIGHT_ON], never from the tile's own time or weight, precisely so
 * that it cannot come out differently anywhere.
 *
 * It still divides by [available], so a wider or narrower tile still gets a cap that fits, and
 * [ceiling] still stops a wide tile growing the numeral down into the switch.
 *
 * The meridiem stays in the max for safety, though tabular minutes are now always the wider of the
 * two: `00` at 0.743 cap is 71.5 dp against `AM`'s 28 dp at 0.145 cap.
 */
private class TimeFit(val cap: Dp, val padStart: Dp, val padEnd: Dp)

@Composable
private fun fittedTime(meridiem: String, tileWidth: Dp, target: Dp): TimeFit {
    val density = LocalDensity.current
    val ref = REFERENCE_CAP_DP.dp
    // `WEIGHT_ON` whatever this tile's weight is, and "00" whatever its time is. Both are
    // deliberate: the answer has to be the *same number on every tile in the grid*, so it may not
    // depend on either. Armed is the wider of the two weights, so solving from it is also what
    // guarantees no tile overflows.
    val hourStyle = ClockFace.numerals(
        ref, ClockFace.CONDENSED, ClockFace.WEIGHT_ON,
        tabular = true, tracking = ClockFace.CONDENSED_TRACKING,
    )
    val minuteStyle = ClockFace.numerals(
        ref * MINUTE_CAP_FRACTION, ClockFace.CONDENSED, ClockFace.WEIGHT_ON,
        tabular = true, tracking = ClockFace.CONDENSED_TRACKING,
    )
    val meridiemStyle = ClockFace.capitals(
        ref * MERIDIEM_FRACTION, ClockFace.CONDENSED, tracking = ClockFace.CONDENSED_TRACKING,
    )
    val h = rememberInkBounds("00", hourStyle)
    val m = rememberInkBounds("00", minuteStyle)
    val a = rememberInkBounds(meridiem, meridiemStyle)

    return remember(h, m, a, tileWidth, target, density) {
        with(density) {
            // **Advance, not ink.** Ink is what varies between digits even when tabular has made
            // the cells identical, so fitting to ink puts the variation straight back into the
            // margins. The advance box is the grid the digits are set on, and it is the thing that
            // has to line up from tile to tile.
            val trailing = if (m.advance >= a.advance) m else a

            // Everything as a multiple of the cap, so the whole thing solves in closed form.
            //  k  is the block's advance
            //  lf is how far the hour's ink starts inside the block's leading edge
            //  rf is how far the trailing ink stops short of its trailing edge
            val k = (h.advance + maxOf(m.advance, a.advance)).toDp() / ref + GAP_FRACTION
            val lf = h.left.toDp() / ref
            val rf = trailing.right.toDp() / ref

            // Solve the cap that puts the **ink**, not the box, exactly [target] from both edges:
            //     target + lf*cap + k*cap + rf*cap + target = tileWidth
            // The two bearings are not equal: the hour's is at full size, the trailing element's at
            // 0.743 of it less a step of negative tracking. That is precisely why padding the box
            // symmetrically cannot square the two visible margins, and why an earlier "shift by half
            // the difference" only ever closed part of the gap. Solving for the ink leaves no gap.
            val cap = (tileWidth - target * 2f) / (k - lf - rf)

            // The block fills exactly, so this is nought. It is kept because it is what makes
            // the two *ink* margins equal rather than the two box margins, if a caller ever
            // clamps the cap.
            val slack = tileWidth - cap * k
            val padStart = (slack + cap * (rf - lf)) / 2f
            TimeFit(cap = cap, padStart = padStart, padEnd = slack - padStart)
        }
    }
}

/** The `wdth` that makes the meridiem as wide as the minutes, and the shift that squares them up. */
private class MeridiemFit(val width: Float, val offsetX: Dp)

/**
 * Stretches `AM`/`PM` on the **width axis** until its ink is exactly as wide as the minutes above
 * it, then nudges it so the two are concentric.
 *
 * Only possible because the minutes are now tabular: their ink is one width for every time, so
 * there is a single target to solve against rather than sixty. Solved by bisection on `wdth`
 * because ink width is monotonic in the axis but not linear in it. Measured, `AM` runs 0.269 of
 * the hour's cap at `wdth` 100 and 0.498 at 151, so a straight interpolation between the ends
 * misses by several dp in the middle.
 *
 * `AM` and `PM` land on different axis values (about 130 and 140), and that is the point: matching
 * their *widths* is what makes them interchangeable in the tile, and no single axis value does that
 * for both. Both sit comfortably inside the font's 25–151 range, so nothing is clamped and no
 * horizontal scale is involved: these are real widths the typeface was drawn to hold.
 */
@Composable
private fun rememberMeridiemFit(
    meridiem: String,
    minutes: String,
    hourCap: Dp,
    weight: Int,
): MeridiemFit {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    return remember(meridiem, minutes, hourCap, weight, density) {
        // Measured at the size it will be drawn at, and **not** at some multiple of it.
        //
        // `getTextBounds` reports whole pixels, so measuring larger and scaling down is the obvious
        // way to keep rounding out of the answer. It is wrong for this face: `opsz` is an axis here
        // and [ClockFace.capitals] drives it from the size, so a 4x measurement is of a differently
        // *shaped* glyph, and the `wdth` it solves comes out 3-6 dp narrow at the real size. Tried
        // and measured; the pixel of rounding it saves is not worth the width it costs.
        val minuteInk = inkBounds(
            resolver, density, minutes,
            ClockFace.numerals(
                hourCap * MINUTE_CAP_FRACTION, ClockFace.CONDENSED, weight,
                tracking = ClockFace.CONDENSED_TRACKING,
            ),
        )
        val cap = hourCap * MERIDIEM_FRACTION
        fun measure(w: Float) = inkBounds(
            resolver, density, meridiem,
            ClockFace.capitals(cap, w, tracking = ClockFace.CONDENSED_TRACKING),
        )

        var lo = ClockFace.CONDENSED
        var hi = 151f
        repeat(16) {
            val mid = (lo + hi) / 2f
            if (measure(mid).width < minuteInk.width) lo = mid else hi = mid
        }
        val width = (lo + hi) / 2f
        val fitted = measure(width)
        // Both are centred in the same column, so their *boxes* line up; the ink inside each does
        // not, because a capital's bearings are nothing like a digit's. Concentric ink is what the
        // eye reads as aligned.
        //
        // Two terms, and the second is easy to miss. Column width is `max(Wm, Wa)` and both boxes
        // are centred in it, so their origins already differ by half the difference of their
        // *advances* before any ink is considered. Leaving that out leaves the meridiem right of
        // where it belongs by an amount that depends on the minutes' bearings: measured, 4.5 px
        // under `15` but 8.0 px under `00`, because a tabular pair of zeros carries 64 units of
        // bearing each side against `15`'s 33 and 71. It reads as "some times line up and some
        // don't", which is exactly how it was reported.
        val boxOffset = (fitted.advance - minuteInk.advance) / 2f
        val minuteMid = minuteInk.left + minuteInk.width / 2f
        val meridiemMid = fitted.left + fitted.width / 2f
        MeridiemFit(width, with(density) { (boxOffset + minuteMid - meridiemMid).toDp() })
    }
}

/** Any size would do; widths are linear in the cap, so this only sets the measurement's precision. */
private const val REFERENCE_CAP_DP = 120f

@Composable
private fun AlarmTile(
    alarm: Alarm,
    order: List<DayOfWeek>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val armed = alarm.enabled
    val container =
        if (armed) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainer
    val ink =
        if (armed) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (armed) ClockFace.WEIGHT_ON else ClockFace.WEIGHT_OFF

    val hour12 = (alarm.time.hour % 12).takeIf { it != 0 } ?: 12
    val meridiem = if (alarm.time.hour < 12) "AM" else "PM"
    val minutesText = "%02d".format(alarm.time.minute)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val tileWidth = maxWidth
    val inset = CONTENT_INSET_DP.dp
    // Sized to fill the tile's **width**, which is the only dimension the grid fixes. `06:40` and
    // `11:22` are one width now that the figures are tabular, so this is one answer for the grid.
    val fit = fittedTime(meridiem = meridiem, tileWidth = tileWidth, target = inset)
    val hourCap = fit.cap
    val meridiemFit = rememberMeridiemFit(meridiem, minutesText, hourCap, weight)
    val timeToDays = hourCap * MERIDIEM_GAP_FRACTION - SWITCH_TOUCH_INSET_DP.dp

    // **The height follows the content**, rather than the content being fitted into a fixed 184x250.
    //
    // That aspect came off the render, and it was right for the render's numerals, which had a cap
    // 0.62 of the tile. Ours is smaller: tabular figures are wider than the proportional ones the
    // render used, and the side inset went from 5 dp to 18, so solving for the width leaves a cap
    // of about 133 where 155 was assumed. A tile still 250 dp tall then has ~34 dp of surplus
    // height, and there is nowhere good to put it. Centring pools it at both ends (35 dp top and
    // bottom against 18 at the sides), while pinning the ends pools it in the middle (a 47 dp hole
    // between the time and the day row). Deriving the height instead means there is no surplus to
    // place: the inset is the same 18 dp on all four sides and the gap is back to the ~7 dp the
    // render specifies.
    //
    // Falls out at about 184x216, and the cap lands at 0.64 of the height. That is near enough
    // the render's 0.62 that the tile still reads as the same design.
    val tileHeight = inset * 2 + hourCap + timeToDays + SWITCH_ROW_VISIBLE_DP.dp

    Surface(
        color = container,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(tileHeight)
            .clickable(onClick = onEdit)
            // One announcement for the whole tile. Otherwise a screen reader finds a bare Switch
            // beside a time it reads out digit by digit, and seven unlabelled day letters.
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append("%d:%02d %s".format(hour12, alarm.time.minute, meridiem))
                    if (alarm.label.isNotBlank()) append(", ${alarm.label}")
                    append(", ${alarm.repeatLabel()}")
                    append(if (armed) ", on" else ", off")
                    append(", double tap to edit")
                }
            },
    ) {
            // The time and the repeat row are **pinned**, not centred, and the gap between them
            // takes up the slack.
            //
            // The render measured two insets and they are the same number: numeral ink 18.1 dp down
            // a 250 dp tile and the switch's visible track 18.2 dp up from the bottom. Both are
            // [CONTENT_INSET_DP]. Even padding all round is the design rather than a preference,
            // and centring the pair cannot produce it. Their combined height is about 34 dp short
            // of what an even inset needs, and centring splits that surplus between the two ends:
            // measured, 36 dp of air above and 35 below against 19 at the sides, close to double.
            // Pinning both ends puts all 34 dp in the one place with nothing to compare it
            // against.
            //
            // The bottom inset is short by [SWITCH_TOUCH_INSET_DP] on purpose: `Switch` hangs 8 dp
            // of invisible touch target below its 32 dp track, so the row's *box* has to stop that
            // much higher for the track you can see to land on the line.
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = inset, bottom = inset - SWITCH_TOUCH_INSET_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(timeToDays),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        // Centred on the **advance box**, plus one grid-wide ink trim.
                        //
                        // The distinction is the whole fix. A *per-tile* ink shift, which is what
                        // this used to do, makes a tile's own two margins equal at the price of
                        // moving the block by a different amount on every tile, which is the
                        // inconsistency you see when two tiles sit side by side. These paddings
                        // come out of a solve against `"00"`, not against this tile, so they are
                        // the same everywhere.
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = fit.padStart, end = fit.padEnd),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Numerals(
                            text = "%02d".format(hour12),
                            capHeight = hourCap,
                            color = ink,
                            width = ClockFace.CONDENSED,
                            weight = weight,
                            tracking = ClockFace.CONDENSED_TRACKING,
                        )
                        Spacer(Modifier.width(hourCap * GAP_FRACTION))
                        // Minutes over meridiem, centred on each other. Those two plus the gap sum
                        // to the hour's cap, so the meridiem lands on the hour's baseline, which is
                        // what the render does.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Numerals(
                                text = minutesText,
                                capHeight = hourCap * MINUTE_CAP_FRACTION,
                                color = ink,
                                width = ClockFace.CONDENSED,
                                weight = weight,
                                tracking = ClockFace.CONDENSED_TRACKING,
                            )
                            Spacer(Modifier.height(hourCap * MERIDIEM_GAP_FRACTION))
                            CapText(
                                text = meridiem,
                                capHeight = hourCap * MERIDIEM_FRACTION,
                                color = ink,
                                width = meridiemFit.width,
                                tracking = ClockFace.CONDENSED_TRACKING,
                                modifier = Modifier.offset(x = meridiemFit.offsetX),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = inset),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DayLetters(alarm, order, ink)
                        Switch(
                            checked = armed,
                            onCheckedChange = { onToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = container,
                                checkedTrackColor = ink,
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = ink,
                                uncheckedTrackColor = Color.Transparent,
                                uncheckedBorderColor = ink,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The repeat days, on the switch's own line.
 *
 * ## One text run, so the spacing is the font's
 *
 * Two earlier attempts gave each letter its own fixed-width box: first every box the same, then
 * each box sized to its own advance. Both are the same mistake in different clothes. A box
 * boundary is a wall the shaper cannot see across, so the side bearings the type designer drew get
 * overridden by an invented cell width and no kerning pair can apply. Equal boxes put 6.9 dp beside
 * `F` and 2.3 dp beside `T`; advance-sized boxes still left slack around every inactive letter,
 * because a light glyph is narrower than the bold cell it was centred in.
 *
 * So all seven are **one** [AnnotatedString], each letter a span carrying its own weight and
 * colour. The text engine then spaces them exactly as the font says to, which is the only
 * definition of "even" that the eye agrees with.
 *
 * ## And the block still cannot change width
 *
 * The switch sits beside this and must land in the same place on every tile, so the run is squeezed
 * to [DAY_BLOCK_DP] by a scale *derived* from its own measured width rather than tuned. A tile with
 * more bold letters squeezes a fraction harder (about 1.5 % between the extremes, some 0.2 dp on
 * a letter), and the block, and so the switch, does not move at all.
 *
 * [DAY_TRACKING_DP] is inside that squeeze, so tightening it hands the space straight to the
 * letters: block width fixed, tracking down, glyphs wider.
 */
@Composable
private fun DayLetters(alarm: Alarm, order: List<DayOfWeek>, ink: Color) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val bold = ClockFace.capitals(DAY_CAP_DP.dp, ClockFace.CONDENSED, weight = DAY_WEIGHT_ON)
    val light = ClockFace.capitals(DAY_CAP_DP.dp, ClockFace.CONDENSED, weight = DAY_WEIGHT_OFF)

    val text = remember(alarm.days, alarm.isOneShot, order, ink, bold, light) {
        buildAnnotatedString {
            order.forEach { day ->
                val on = !alarm.isOneShot && day in alarm.days
                withStyle(
                    SpanStyle(
                        // Weight is the state here too, matching the numerals above.
                        fontFamily = (if (on) bold else light).fontFamily,
                        color = if (on) ink else ink.copy(alpha = 0.30f),
                    )
                ) {
                    append(day.name.take(1))
                }
            }
        }
    }

    // Tracking in em, so it rides the type size the way the rest of the face's tracking does.
    val style = bold.copy(
        letterSpacing = with(density) { (DAY_TRACKING_DP.dp / bold.fontSize.toDp()).em },
    )
    val scaleX = with(density) {
        DAY_BLOCK_DP.dp.toPx() / measurer.measure(text, style).size.width
    }

    StretchedCaps(text = text, style = style, capHeight = DAY_CAP_DP.dp, scaleX = scaleX)
}

/** Weight is the state, the same way it is on the numerals above. Not a second colour ramp. */
private const val DAY_WEIGHT_ON = 700
private const val DAY_WEIGHT_OFF = 400
