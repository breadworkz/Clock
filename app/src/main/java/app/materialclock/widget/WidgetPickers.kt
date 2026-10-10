package app.materialclock.widget

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The two pickers the widget editor is built from.
 *
 * Both are the variant the spec names for the job, which is not the variant that comes to hand
 * first:
 *
 *  - **Connected** button group, not standard. The spec is explicit that *"connected button
 *    groups help people select options, switch views, or sort elements"* and *"should replace the
 *    baseline segmented button, which is no longer recommended"*, while a standard group is for a
 *    row of unrelated actions and reflows its neighbours when one is pressed. These are
 *    single-choice selections, so they are connected: **2 dp between buttons at every size**, a
 *    fully round outer shape and square inner corners, and selecting one changes only its own
 *    shape.
 *  - **Multi-browse** carousel, not uncontained. *"Best for browsing many items at once, like
 *    photos … snap-scrolling is recommended to ensure items are recognizable"*. Uncontained is for
 *    "highly-customized or text-heavy" rows, which a strip of shape thumbnails is not.
 *
 * Item corner radius is 28 dp and the paddings are 16 dp leading/trailing, 8 dp between elements,
 * all straight from the carousel spec sheet.
 */

/** Five or more is a carousel; fewer is a connected group you can see all of at once. */
const val CAROUSEL_MIN = 5

private val ITEM_CORNER = 28.dp
private val CAROUSEL_EDGE = 16.dp
private val CAROUSEL_GAP = 8.dp

/**
 * A single-choice **connected** button group.
 *
 * `ToggleButton` carries the checked state into both the fill and the shape morph, which is what
 * the spec asks a selected button in a group to do. The leading, middle and trailing shape sets
 * differ (round on the outside of the group, square where two buttons meet), so the row reads as
 * one control rather than as three separate pills.
 *
 * `selectableGroup()` is supplied by hand because `ButtonGroup` and its shapes ship no semantics at
 * all; without it a screen reader announces N unrelated toggles instead of one set of choices.
 */
@Composable
fun <T> OptionGroup(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = CAROUSEL_EDGE).padding(top = ROW_GAP / 2, bottom = ROW_GAP / 2)) {
        PickerLabel(label)
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { i, option ->
                val shapes = when {
                    options.size == 1 -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    i == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    i == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                ToggleButton(
                    checked = option == value,
                    onCheckedChange = { if (it) onSelect(option) },
                    shapes = shapes,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        // Role.Tab is wrong here, and the Role.Checkbox that ToggleButton
                        // hardcodes says "independent on/off". These are mutually exclusive, so
                        // they are radio buttons as far as anything reading the screen is
                        // concerned.
                        .semantics { role = Role.RadioButton },
                ) {
                    Text(
                        optionLabel(option),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * A multi-browse carousel of drawn thumbnails.
 *
 * Selection inverts the cell rather than ticking it: a tick would cover the very thing being
 * chosen, and the inversion reads at a glance across a scrolling strip.
 *
 * One known gap against the spec, stated rather than hidden: *"on vertically-scrolling pages,
 * carousels require an accessible way to view all the items without horizontally scrolling."* The
 * items are reachable by accessibility focus, which scrolls the strip, but there is no separate
 * grid view of them.
 */
@Composable
fun <T> OptionCarousel(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    itemSize: Dp = 84.dp,
    /**
     * Whether each cell carries its own name underneath.
     *
     * Off for the face carousel: a drawn clover *is* its own label, and "Clover, 8 leaf" under it
     * adds a line of text that says less than the picture already did while costing a strip of
     * height on every row.
     */
    itemLabels: Boolean = true,
    onSelect: (T) -> Unit,
    thumbnail: @Composable (T, Boolean) -> Unit,
) {
    val selectedIndex = options.indexOf(value).coerceAtLeast(0)
    val state = rememberCarouselState(initialItem = selectedIndex) { options.size }

    Column(Modifier.padding(top = ROW_GAP / 2, bottom = ROW_GAP / 2)) {
        PickerLabel(label, Modifier.padding(start = CAROUSEL_EDGE))
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = itemSize,
            itemSpacing = CAROUSEL_GAP,
            contentPadding = PaddingValues(horizontal = CAROUSEL_EDGE),
            modifier = Modifier
                .fillMaxWidth()
                .height(itemSize + if (itemLabels) LABEL_STRIP else 0.dp),
        ) { index ->
            val option = options[index]
            val selected = option == value
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(itemSize)
                    .selectableGroup(),
            ) {
                Box(
                    Modifier
                        .width(itemSize)
                        .height(itemSize)
                        .clip(RoundedCornerShape(ITEM_CORNER))
                        // Selection *inverts* the cell rather than ringing it: the container takes
                        // the accent and the drawn thing takes the container's colour. A border
                        // around a shape thumbnail competes with the shape's own outline; on a
                        // squircle or a rounded square the two read as one doubled edge. It also
                        // eats 3 dp off every side of the artwork it is meant to be highlighting.
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    thumbnail(option, selected)
                }
                if (itemLabels) {
                    Text(
                        optionLabel(option),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(itemSize).padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * One vertical rhythm for the whole editor, so nothing is a stray few dp out.
 *
 * Each row pads by half of it top and bottom, so this is the gap *between* two rows. It is
 * deliberately smaller than the header-to-control gap is large. A label belongs to the control
 * under it, so the space *above* a header has to stay clearly bigger than the space below it;
 * 16 dp between rows against 8 dp under a label is what groups them.
 */
val ROW_GAP = 16.dp
private val LABEL_STRIP = 24.dp

@Composable
private fun PickerLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/** A bitmap thumbnail, scaled into the carousel cell. */
@Composable
fun ThumbnailImage(bitmap: Bitmap, description: String?) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().padding(12.dp),
    )
}
