package app.materialclock.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.materialclock.data.HourFormat
import app.materialclock.data.WorldClockSettings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.materialclock.core.WorldCity
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The world clock.
 *
 * Its hero is the concept's own idea and, as far as I can find, unique to it: **one dial with a
 * hand per city**, each hand tipped with a capsule carrying that city's name. Where every other
 * world clock makes you read six rows to answer "who is awake", this answers it as a shape. The
 * cities clustered near the bottom of the dial are the ones in the small hours.
 *
 * Its honest failure mode is on screen too: two cities on the same offset put their capsules in
 * exactly the same place. London and Algiers do it for most of the year. The dial draws them in
 * order and the list underneath is the fallback, which is why the list is not optional.
 */
private const val ROW_HEIGHT_DP = 128

@Composable
fun WorldClockScreen(
    cities: List<WorldCity>,
    home: ZoneId,
    nowUtcMillis: Long,
    settings: WorldClockSettings,
    onRemove: (WorldCity) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val use24h = when (settings.hourFormat) {
        HourFormat.SYSTEM -> android.text.format.DateFormat.is24HourFormat(LocalContext.current)
        HourFormat.H12 -> false
        HourFormat.H24 -> true
    }
    val measurer = rememberTextMeasurer()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            CityDial(
                cities = cities,
                nowUtcMillis = nowUtcMillis,
                measurer = measurer,
                modifier = Modifier
                    .fillMaxWidth()
                    // Not a full-width circle: at 130 dp per row a 372 dp dial leaves room for
                    // barely one city, and the list is the dial's fallback for colliding offsets.
                    .padding(start = 54.dp, end = 54.dp, top = 4.dp, bottom = 18.dp)
                    .aspectRatio(1f),
            )
        }
        items(cities, key = { it.zone.id }) { city ->
            CityRow(
                city = city,
                home = home,
                nowUtcMillis = nowUtcMillis,
                use24h = use24h,
                showSeconds = settings.showSeconds,
                onRemove = { onRemove(city) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun CityDial(
    cities: List<WorldCity>,
    nowUtcMillis: Long,
    measurer: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    val face = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val ghost = MaterialTheme.colorScheme.onSurfaceVariant

    val hourStyle = MaterialTheme.typography.headlineMedium.copy(color = ghost.copy(alpha = .45f))
    val pillStyle = MaterialTheme.typography.labelSmall.copy(
        color = onAccent,
        fontWeight = FontWeight.SemiBold,
    )

    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(face, radius = r, center = c)

        // 12 / 3 / 6 / 9 set large and ghosted; the rest are dots. Straight from the render.
        for (i in 0 until 12) {
            val a = (i / 12f) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val p = Offset(c.x + cos(a) * (r - 44.dp.toPx()), c.y + sin(a) * (r - 44.dp.toPx()))
            if (i % 3 == 0) {
                val label = if (i == 0) "12" else i.toString()
                val m = measurer.measure(label, hourStyle)
                drawText(m, topLeft = Offset(p.x - m.size.width / 2f, p.y - m.size.height / 2f))
            } else {
                drawCircle(accent.copy(alpha = .45f), radius = 3.5.dp.toPx(), center = p)
            }
        }

        cities.forEach { city ->
            val local = city.timeAt(nowUtcMillis)
            val hours12 = (local.hour % 12) + local.minute / 60f
            val angle = hours12 / 12f * 360f - 90f
            val rad = angle * PI.toFloat() / 180f
            val len = r - 62.dp.toPx()
            val tip = Offset(c.x + cos(rad) * len, c.y + sin(rad) * len)

            drawLine(accent, start = c, end = tip, strokeWidth = 2.dp.toPx())

            val m = measurer.measure(city.city, pillStyle)
            val padH = 7.dp.toPx()
            val w = m.size.width + padH * 2
            val h = m.size.height + 5.dp.toPx()
            // Flip through 180° in the lower half so a name is never upside down, which is what
            // the render does: its upper-left hands read normally rather than mirrored.
            val flip = if (angle.mod(360f) in 90f..270f) 180f else 0f
            rotate(degrees = angle + flip, pivot = tip) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(tip.x - w / 2f, tip.y - h / 2f),
                    size = Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
                )
                drawText(
                    m,
                    topLeft = Offset(tip.x - m.size.width / 2f, tip.y - m.size.height / 2f),
                )
            }
        }
        drawCircle(accent, radius = 7.dp.toPx(), center = c)
    }
}

/**
 * A city row.
 *
 * Measured, and much larger than a list item: **128 dp tall on a 2 dp gap**, so the pitch is 130.
 * The right end is a true semicircle (the corner radius is exactly half the row height), not a
 * 24-28 dp rounded corner. Two earlier passes had these at 56 and then 88 dp, and both read as an
 * ordinary list; at 128 the time can be set at display size, which is the point. The time is the
 * content and the city is its label, not the other way round.
 */
/**
 * A city, removable by swiping it away to the left.
 *
 * `SwipeToDismissBox` is the component for this, and the spec's rule for a swipeable container is
 * "one swipe action", which is why only end-to-start is enabled and start-to-end is off, rather
 * than offering two directions that do the same thing. The revealed layer is the error container
 * with a trailing bin, which is the standard destructive treatment and reads before the row has
 * travelled far enough to commit.
 *
 * It replaces a long-press and a confirmation dialog. The dialog was defensible while the gesture
 * was invisible; with a swipe, the gesture is deliberate enough to stand on its own, and the safety
 * net moves to an Undo snackbar. That is both the documented mitigation and less work to use than a
 * dialog you have to answer every time.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CityRow(
    city: WorldCity,
    home: ZoneId,
    nowUtcMillis: Long,
    use24h: Boolean,
    showSeconds: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val local = city.timeAt(nowUtcMillis)
    val night = city.isNight(nowUtcMillis)
    val clock = buildString {
        if (use24h) {
            append("%02d:%02d".format(local.hour, local.minute))
        } else {
            append("%d:%02d".format((local.hour % 12).takeIf { it != 0 } ?: 12, local.minute))
        }
        if (showSeconds) append(":%02d".format(local.second))
        if (!use24h) append(if (local.hour < 12) "am" else "pm")
    }

    // A plain `remember`, not `rememberSwipeToDismissBoxState`.
    //
    // That helper is *saveable* and LazyColumn restores saved state per item key, so a city that
    // is swiped away and then comes back, whether by Undo or by adding it again, lands on its old
    // saved `EndToStart` value. `SwipeToDismissBox` then sees a box that is already dismissed and
    // fires `onDismiss` on its first composition, deleting the row the instant it returns. The
    // symptom is baffling from the outside: the store logs the city restored and the screen stays
    // empty. Swipe offset is transient gesture state and has no business outliving the row.
    val state = remember(city.zone.id) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = { distance: Float -> distance * 0.5f },
        )
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        // One swipe action per container, per the spec. Two directions doing the same thing is not
        // twice the affordance, it is a coin flip about whether the row came back.
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
        backgroundContent = {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(ROW_HEIGHT_DP.dp),
            ) {
                Box(Modifier.fillMaxSize().padding(end = 32.dp), Alignment.CenterEnd) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(ROW_HEIGHT_DP.dp)
                .semantics {
                    // A swipe is a gesture, and a gesture is invisible to a screen reader. The same
                    // action has to exist as a verb it can announce and perform.
                    customActions = listOf(
                        CustomAccessibilityAction("Remove ${city.city}") { onRemove(); true }
                    )
                },
        ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Day or night, not an initial. The initial says nothing you cannot read in the name
            // beside it; whether they are awake is the only thing a world clock is actually for.
            Surface(
                color = if (night) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = CircleShape,
                modifier = Modifier.size(51.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (night) Icons.Outlined.Bedtime else Icons.Outlined.LightMode,
                        contentDescription = if (night) "night" else "daytime",
                        tint = if (night) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 9.6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    city.city,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${city.region} · ${city.offsetLabel(home, nowUtcMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                clock,
                style = if (showSeconds) {
                    // Seconds add three glyphs to a row that was already tight at four.
                    MaterialTheme.typography.titleLargeEmphasized
                } else {
                    MaterialTheme.typography.headlineMediumEmphasized
                },
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
    }
}
