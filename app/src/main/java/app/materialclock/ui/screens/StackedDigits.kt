package app.materialclock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.materialclock.ui.theme.ClockFace
import app.materialclock.ui.theme.Numerals

/**
 * The concept's timer readout: **one pair of digits per line**, right-aligned, with the
 * separators drawn as two large dots rather than set as a colon.
 *
 * Three lines of two digits instead of one line of eight is what lets the numerals be this big.
 * On one line, `00:00:09` across a 412 dp screen caps out around 60 dp of cap height, while
 * stacked it reaches 77 dp. The concept is not being decorative here; the stack is the only way to
 * get a glanceable countdown onto a phone.
 *
 * Measured off the render: the three rows are in exact register, laid out as a **3-cell tabular
 * string** (one separator cell plus two digit cells, cell advance 67.2 dp), and that block is
 * **centred on the screen**, not right-aligned. Row pitch is 115 dp baseline to baseline, an ink
 * gap of 35.2 dp. The separator dots sit in the leading cell on the baseline, 18 dp across with
 * their centres 40.8 dp apart, and row one's pictogram is centred in that same cell.
 *
 * The readout has its own cut of the same family, `wdth` 95 / `wght` 490 with the slashed zero.
 * That cut was measured off the render rather than borrowed from the alarm grid, which is a much
 * narrower and much lighter face and looked nothing like it.
 */
@Composable
fun StackedDigits(
    parts: List<String>,
    capHeight: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    label: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (label != null) {
                    Modifier.clearAndSetSemantics { contentDescription = label }
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        // 35.2 dp measured. `Numerals` boxes are ink-tight, so this is the true ink gap with no
        // ascent padding quietly padding it out.
        verticalArrangement = Arrangement.spacedBy(35.dp),
    ) {
        parts.forEachIndexed { i, part ->
            DigitRow(
                part = part,
                capHeight = capHeight,
                color = color,
                leading = leading.takeIf { i == 0 },
                separator = i > 0,
            )
        }
    }
}

/**
 * One line of the readout: a leading cell holding a pictogram, colon dots or nothing, followed by
 * the digit pair.
 *
 * Public because things have to line up with it. The stopwatch's Start button is exactly as wide as
 * a `: ss` row, and the only way to be *exactly* that is to measure this composable rather than to
 * add up a cell constant and a guess at the digit advance.
 */
@Composable
fun DigitRow(
    part: String,
    capHeight: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    separator: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // The separator's own tabular cell, the same advance as a digit cell.
        Box(Modifier.width(SEPARATOR_CELL_DP.dp), contentAlignment = Alignment.Center) {
            when {
                leading != null -> androidx.compose.material3.Icon(
                    leading,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(46.dp),
                )
                separator -> ColonDots(color)
            }
        }
        Numerals(
            text = part,
            capHeight = capHeight,
            color = color,
            // The concept's Plank cut: 0.66 wide over tall, stroke 0.139 of cap, slashed zero. Not
            // the alarm grid's condensed face and not its light off-weight.
            width = ClockFace.TIMER_WIDTH,
            weight = ClockFace.TIMER_WEIGHT,
            slashedZero = true,
        )
    }
}

/** Measured: 18 dp dots with their centres 40.8 dp apart, so 22.8 dp of clear space between. */
private const val SEPARATOR_CELL_DP = 67.2f
private val DOT_DIAMETER = 18.dp

/**
 * How far a row's **visible** left edge sits inside its layout box.
 *
 * The separator gets a whole tabular cell of the same advance as a digit, and the colon's dots are
 * centred in it. So the box starts a good deal to the left of any ink, and anything that lines up
 * with the box rather than with the colon comes out looking off-centre even though the number is
 * right. Derived from the two constants above rather than measured off a screenshot, so it cannot
 * drift away from them.
 */
val ROW_INK_INSET: Dp = (SEPARATOR_CELL_DP.dp - DOT_DIAMETER) / 2

@Composable
private fun ColonDots(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(22.8.dp)) {
        repeat(2) {
            Surface(color = color, shape = CircleShape, modifier = Modifier.size(DOT_DIAMETER)) {}
        }
    }
}

/**
 * The wide action pill the concept puts under its timer.
 *
 * Measured at **136 dp tall** and fully rounded: an M3 Expressive extra-large button. This is the
 * one control on the screen, so it gets the size that says so. M3E tactic 4, contain for emphasis,
 * in its most literal form. An earlier build had it at 78 dp, which read as an ordinary button.
 */
/** Measured off the render's Pause button. */
const val PILL_HEIGHT_DP = 136f

@Composable
fun WidePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    icon: ImageVector? = null,
    outlined: Boolean = false,
    // Only the primary action gets the extra-large treatment. The concept's timer screen has
    // exactly one button; giving every secondary action 136 dp overflows the screen and Compose
    // then squeezes them all, which looks worse than either size chosen deliberately.
    height: Dp = PILL_HEIGHT_DP.dp,
    // Three pills abreast leave about 120 dp each, and "Reset" at headline-small inside 24 dp of
    // padding does not fit that. The compact cut trades the display-scale label for a title one
    // rather than letting the text ellipsise, which is the one thing a button must never do.
    compact: Boolean = false,
    /**
     * Off makes the pill exactly as wide as its own label and icon.
     *
     * Needed by the stopwatch, whose Start button is a lone control that has no business spanning
     * the screen. It is needed *measurably*, too, because the morph that grows it into the
     * three-button row has to know the width it grew from.
     */
    fillWidth: Boolean = true,
    contentPaddingH: Dp = if (compact) 10.dp else 24.dp,
    /**
     * Fades the label and icon *without* touching the container.
     *
     * The stopwatch needs the two to move independently: when the Start pill elongates, its content
     * has to be gone almost immediately, because text sliding along inside a growing container
     * reads as a rendering glitch, while the container itself stays solid all the way to full
     * width.
     */
    contentAlpha: Float = 1f,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier).height(height),
        shape = CircleShape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (outlined) Color.Transparent else container,
            contentColor = content,
        ),
        border = if (outlined) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = contentPaddingH),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { alpha = contentAlpha },
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 22.dp else 30.dp),
                )
                Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
            }
            androidx.compose.material3.Text(
                text,
                style = if (compact) {
                    MaterialTheme.typography.titleLargeEmphasized
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                maxLines = 1,
            )
        }
    }
}
