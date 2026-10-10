package app.materialclock.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.materialclock.R

/**
 * The clock's numerals: **Google Sans Flex, driven on its own axes.** No custom glyphs.
 *
 * ## What this replaces, and why the first attempt was wrong
 *
 * The M3 Expressive research-concept clock sets its alarm times in a face whose `0` is about one
 * fifth as wide as it is tall. An earlier build concluded no licensable font could do that and
 * hand-drew ten digits as vector paths. That was wrong twice over.
 *
 * First, Google Sans Flex went open-source in 2025 (`docs/m3/library/google-sans-flex-font.md`)
 * and has **six axes**: weight, width, optical size, slant, grade and roundedness. The reason the
 * earlier measurement missed `wdth` is that `tools/build_gsflex.py`'s predecessor asked
 * `fonts.googleapis.com` for `opsz,wght` only, and gstatic answers such a request with a *partial
 * instance*: the other four axes are flattened to their defaults and struck from `fvar`. The font
 * in the APK genuinely had two axes. The font Google ships has six.
 *
 * Measured on the correctly-built binary, the `0` glyph gives:
 *
 * | `wdth` | `wght` | ink w/h | matches |
 * |---|---|---|---|
 * | 25 | 250 | 0.234 | the concept's alarm tiles (~0.20) |
 * | 100 | 250 | 0.699 | the concept's Plank timer (~0.72) |
 *
 * So the two cuts the concept uses are one font at two width settings, and there was never
 * anything to draw.
 *
 * Second, the *off* state is not an outline. Magnifying a disarmed stem shows a single uniform
 * stroke with no inner contour: it is a **lighter weight of the same face**. The earlier "proof"
 * of outlining was a bad test: scanning across a `0` gives stroke-hole-stroke for any zero, because
 * a zero has a counter. State lives on the `wght` axis, which is what a variable font is for.
 */
object ClockFace {

    /** Ultra-condensed, for the alarm grid. */
    const val CONDENSED = 25f

    /** Normal width, for body text and labels. */
    const val NORMAL = 100f

    /**
     * The timer and stopwatch readout, measured off the concept's Plank screen: a `0` that is
     * 0.66 as wide as it is tall with a stroke 0.139 of the cap, plus a **slashed zero**, which
     * Google Sans Flex ships as the `zero` OpenType feature. Far heavier than the alarm grid's off
     * state, which is what an earlier build wrongly reused here.
     */
    const val TIMER_WIDTH = 95f
    const val TIMER_WEIGHT = 490

    /**
     * Armed. Measured, not picked: the armed stem measures 0.0639 of the cap height in the render,
     * and this binary hits it at `wght` 261 with `opsz` 144 / `wdth` 25 (Brent-solved, then
     * verified by two independent rasterisers). The value is opsz-dependent, so do not quote it
     * without opsz=144.
     */
    const val WEIGHT_ON = 260

    /**
     * Disarmed. The off stem measures 0.0306 of the cap, almost exactly half the armed weight,
     * which solves to `wght` 119. A light weight at display size is explicitly sanctioned by the
     * spec, so this is not a hack: it is the documented use of the axis.
     */
    const val WEIGHT_OFF = 120

    /**
     * Digit ink height as a fraction of the em, measured off the bundled binary
     * (1455/2000 at `wdth` 25). Converts a wanted cap height into a font size.
     */
    const val DIGIT_EM = 0.728f

    /**
     * Distance from a text box's top edge down to the top of a digit, in em.
     *
     * `hhea.ascent` is 0.966 em and a digit reaches 0.728 em above the baseline, so with
     * `includeFontPadding = false` the ink starts 0.238 em below the box top. [Numerals] offsets
     * by exactly this, which is what makes a numeral's layout box equal to its ink.
     */
    const val INK_TOP_EM = 0.966f - DIGIT_EM

    fun fontSizeFor(capHeight: Dp): Dp = capHeight / DIGIT_EM

    /**
     * Capital cap height as a fraction of the em: `OS/2.sCapHeight` = 1432/2000, corroborated by
     * the flat capitals T, M, W, F and H all measuring exactly 1432 units. ('S' is 1475, because
     * round letters overshoot, which is why a single sampled glyph is the wrong thing to
     * calibrate on.)
     *
     * This was 0.604 for a while, read off a screenshot of the meridiem whose segmentation was
     * wrong. Everything sized through here came out 19% too big, which is how the day letters
     * ended up 38.5 dp tall next to a 32 dp switch.
     */
    private const val CAPITAL_EM = 0.716f

    /** Distance from a text box's top down to a capital's top, in em. */
    const val CAPITAL_INK_TOP_EM = 0.966f - CAPITAL_EM

    fun capitalSizeFor(capHeight: Dp): Dp = capHeight / CAPITAL_EM

    private val cache = HashMap<Int, FontFamily>()

    /** Google Sans Flex pinned to one point in axis space. */
    fun family(
        opticalSize: Float,
        width: Float = NORMAL,
        weight: Int = 400,
        grade: Float = 0f,
        roundness: Float = 0f,
    ): FontFamily {
        val key = (opticalSize.toInt() * 31 + width.toInt()) * 31 * 31 +
            weight * 31 + grade.toInt() * 7 + roundness.toInt()
        return cache.getOrPut(key) {
            FontFamily(
                Font(
                    resId = R.font.google_sans_flex,
                    weight = FontWeight(weight.coerceIn(1, 1000)),
                    variationSettings = FontVariation.Settings(
                        FontVariation.Setting("opsz", opticalSize.coerceIn(6f, 144f)),
                        FontVariation.Setting("wdth", width.coerceIn(25f, 151f)),
                        FontVariation.Setting("wght", weight.coerceIn(1, 1000).toFloat()),
                        FontVariation.Setting("GRAD", grade.coerceIn(0f, 100f)),
                        FontVariation.Setting("ROND", roundness.coerceIn(0f, 100f)),
                    ),
                ),
            )
        }
    }

    /**
     * A numeral style whose box hugs the glyphs.
     *
     * `includeFontPadding = false` plus a trimmed line height is what makes the box predictable:
     * left as-is, Android adds the font's own ascent/descent padding and every measurement taken
     * off the concept renders lands in the wrong place by a different amount at each size.
     */
    /** The meridiem's style, exposed so a caller can measure it before deciding its tracking. */
    fun capitals(capHeight: Dp, width: Float = 75f, weight: Int = 500, tracking: Float = 0f): TextStyle {
        val size = capitalSizeFor(capHeight)
        return TextStyle(
            fontFamily = family(opticalSize = size.value, width = width, weight = weight),
            fontSize = size.value.sp,
            letterSpacing = tracking.em,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }

    fun numerals(
        capHeight: Dp,
        width: Float,
        weight: Int,
        tabular: Boolean = true,
        tracking: Float = 0f,
        slashedZero: Boolean = false,
    ): TextStyle = TextStyle(
        // opsz tracks the type size, clamped at the font's 144 max. Every one of the 30
        // documented styles sets opsz equal to its point size; the Do is explicit.
        fontFamily = family(
            opticalSize = fontSizeFor(capHeight).value,
            width = width,
            weight = weight,
        ),
        fontSize = fontSizeFor(capHeight).value.sp,
        letterSpacing = tracking.em,
        /*
         * Tabular by default, and the alarm grid uses it too.
         *
         * An earlier version of this comment argued the opposite, claiming tabular was right for a
         * ticking stopwatch but wrong for a static alarm time, because the concept's tiles look
         * proportional and forcing tabular "pads every 1 out to a zero's width". Measured against
         * this binary at `wdth` 25, that last claim is simply not true: `one.tf` has 301 units of
         * ink against `zero.tf`'s 344, so a tabular 1 here is 88% as wide as a 0, not half. At this
         * width the tabular 1 carries a full flag and reads as a digit, not as a stick in a box.
         *
         * What proportional actually costs is measurable and large. Digit advances at `wdth` 25
         * run from 324 (`1`) to 449 (`8`), a 39% spread, so a four-digit time varies from 0.645
         * to 0.820 em. On a 174 dp tile that is a 45 dp swing in the block's width, which lands as
         * a 22.6 dp swing in the side margins from one tile to the next. Tabular makes every time
         * exactly one width, which is the only way a *grid* of times can line up with itself.
         */
        fontFeatureSettings = buildString {
            append(if (tabular) "tnum, lnum" else "pnum, lnum")
            // The concept's timer digits are slashed zeros. `zero` is a real feature on this
            // binary, so this is the font's own alternate rather than anything drawn.
            if (slashedZero) append(", zero")
        },
        // Leaving this on adds the font's own ascent/descent padding, and every measurement taken
        // off the concept renders then lands wrong by a different amount at each size.
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    /**
     * Optical tracking for the condensed cut, in em, applied to **every** element of a time:
     * hour, minutes and meridiem alike.
     *
     * One value on purpose. An earlier version measured the minutes and solved a per-element
     * tracking so the meridiem justified to their exact width, which is what the render does; it
     * also meant the letter fit changed depending on which digits were showing, and that reads as
     * sloppy rather than as precise. A single em value keeps the fit identical everywhere and
     * scales with each element's own size for free.
     *
     * The spec sets tracking to 0 across the scale, so this is an optical correction and not spec.
     * It is needed because this binary at `wdth` 25 advances about 0.30 of cap height per digit
     * where the render's face advances 0.22; the ink matches, the side bearings do not.
     */
    /**
     * The last nudge to the tracking, kept as its own term rather than folded into the number
     * below, because the alarm tile subtracts exactly twice it from the gap between the hour and
     * the minutes. Widening the digit gaps and narrowing the block gap by the same total keeps the
     * time the width it was; burying the delta would make that relationship impossible to see, and
     * the next person to adjust one would silently break the other.
     */
    const val TRACKING_DELTA = 0.004f
    const val CONDENSED_TRACKING = -0.014f + TRACKING_DELTA

}

/**
 * The horizontal ink bounds of [text] in [style], in pixels: the offset from the pen origin to the
 * first ink, and the width of the ink itself.
 *
 * Compose reports *advance* widths, which include each glyph's side bearings. At `wdth` 25 those
 * are not a rounding error. This face advances about 0.30 of the cap per digit where its ink is
 * 0.22 wide, so a numeral laid out at x = 0 has its first stroke some way inside its own box.
 * Anything that has to sit flush against an edge, or be fitted to an exact width, needs the ink.
 *
 * `Paint.getTextBounds` is the only API that gives it. The Typeface comes from Compose's own
 * resolver, so the variation settings baked into the family (the whole point of this file) are
 * the ones measured; constructing a Typeface by hand here would silently measure `wdth` 100.
 */
class InkBounds(val left: Float, val width: Float, val advance: Float) {
    /** Distance from the last ink to the end of the reserved box. */
    val right: Float get() = advance - left - width
}

@Composable
fun rememberInkBounds(text: String, style: TextStyle): InkBounds {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    return remember(text, style, density) { inkBounds(resolver, density, text, style) }
}

/**
 * The measuring half of [rememberInkBounds], callable outside composition.
 *
 * Exposed so a caller can *solve* against it, bisecting a variable-font axis until a string
 * measures the width it has to be. That needs many measurements per frame and so cannot be a
 * `@Composable` loop.
 */
fun inkBounds(
    resolver: FontFamily.Resolver,
    density: Density,
    text: String,
    style: TextStyle,
): InkBounds {
    return run {
        val typeface = resolver.resolve(
            fontFamily = style.fontFamily,
            fontWeight = style.fontWeight ?: FontWeight.Normal,
            fontStyle = FontStyle.Normal,
            fontSynthesis = FontSynthesis.All,
        ).value as android.graphics.Typeface
        val paint = android.graphics.Paint().apply {
            this.typeface = typeface
            textSize = with(density) { style.fontSize.toPx() }
            letterSpacing = style.letterSpacing.value
            // **Including the feature settings.** Leaving these off measured the font's default
            // figures while the caller rendered `tnum` ones, and at `wdth` 25 those are not close:
            // a tabular zero advances 472 units against a proportional 438. Every advance and every
            // bearing this returns was then ~7% adrift from what would actually be drawn, which is
            // invisible until something tries to fit ink to an edge with it.
            fontFeatureSettings = style.fontFeatureSettings
            isAntiAlias = true
        }
        val r = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, r)
        // Left bearing, ink width, and the advance the layout will actually reserve. The third is
        // what lets a caller work out the *right* bearing, which is the other half of centring ink.
        InkBounds(r.left.toFloat(), r.width().toFloat(), paint.measureText(text))
    }
}

/**
 * A numeral whose layout box **is its ink**, with no ascent above and no descent below.
 *
 * Digits have no descender and their tops are all one height, so a box clipped to exactly the cap
 * height is honest. It is also what makes composition possible: two numerals at different sizes
 * top-align by simply sitting in a `Row`, with no magic offset, because neither carries invisible
 * padding that scales with its font size. That invisible padding is what "nothing is aligned
 * correctly" actually was.
 */
/**
 * Capitals in an ink-tight box, for the meridiem.
 *
 * It needs the same treatment as [Numerals] for the same reason, and here the reason is exact: the
 * render composes minutes cap + gap + AM cap to equal the hour's cap, i.e. the AM sits on the
 * hour's baseline. A text box carrying its own ascent and descent makes that sum impossible to
 * hit, and the whole tile's content then measures taller than the hour and floats upward.
 */
@Composable
fun CapText(
    text: String,
    capHeight: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    width: Float = 75f,
    weight: Int = 500,
    tracking: Float = 0f,
) {
    val style = ClockFace.capitals(capHeight, width, weight, tracking).copy(color = color)
    InkTight(text, style, capHeight, modifier)
}

/**
 * Capitals set to an exact cap height and squeezed to an exact width.
 *
 * For the alarm tiles' day letters, which have to stand as tall as the 32 dp switch beside them
 * while seven of them share about 78 dp. That cannot be done on the width axis: even at the `wdth`
 * 25 floor, "SMTWTFS" at a 32 dp cap measures 120 dp. So the axis goes to its floor first and a
 * horizontal scale closes the rest. Unlike an outlined numeral, where a squeeze thins the
 * verticals and leaves the horizontals alone, these are small solid glyphs where it does not show.
 *
 * A transform does not change layout width, so the reported width is scaled here too; otherwise
 * the row would claim 120 dp and shove the switch off the tile.
 */
@Composable
fun StretchedCaps(
    text: String,
    capHeight: Dp,
    color: Color,
    scaleX: Float,
    modifier: Modifier = Modifier,
    width: Float = ClockFace.CONDENSED,
    weight: Int = 600,
) {
    val style = ClockFace.capitals(capHeight, width, weight).copy(color = color)
    StretchedCaps(AnnotatedString(text), style, capHeight, scaleX, modifier)
}

/**
 * The same squeeze, over a string whose runs carry their own styling.
 *
 * This is the one the day letters use. Setting seven letters as **one** text run rather than seven
 * boxed ones is what makes their spacing the font's own: the shaper applies each glyph's side
 * bearings and any kerning pair between them, which is precisely the thing that "is the kerning
 * between each day consistent?" is asking about. Seven separate boxes cannot kern across a box
 * boundary, and any per-letter cell width invented to stand in for that is a worse guess than the
 * one the type designer already made.
 *
 * [scaleX] is then derived from the measured run by squeezing the whole line to the block's width,
 * so tracking and letterform are squeezed by the same factor and the relative fit never changes.
 */
@Composable
fun StretchedCaps(
    text: AnnotatedString,
    style: TextStyle,
    capHeight: Dp,
    scaleX: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val layout = remember(text, style) { measurer.measure(text, style) }
    Layout(
        content = {
            BasicText(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    this.scaleX = scaleX
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            )
        },
        modifier = modifier,
    ) { measurables, _ ->
        val placeable = measurables.first().measure(
            Constraints(maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity),
        )
        val inkTop = (layout.firstBaseline - capHeight.toPx()).toInt()
        // A transform does not change layout width, so the reported width is scaled here too;
        // otherwise the row claims its unsqueezed width and shoves the switch off the tile.
        layout((placeable.width * scaleX).toInt(), capHeight.roundToPx()) {
            placeable.place(0, -inkTop)
        }
    }
}

@Composable
fun Numerals(
    text: String,
    capHeight: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    width: Float = ClockFace.NORMAL,
    weight: Int = ClockFace.WEIGHT_ON,
    tabular: Boolean = true,
    tracking: Float = 0f,
    slashedZero: Boolean = false,
) {
    val style = ClockFace.numerals(capHeight, width, weight, tabular, tracking, slashedZero)
        .copy(color = color)
    InkTight(text, style, capHeight, modifier)
}

/**
 * Lays text out so its **layout box is exactly the ink**: cap height, nothing above, nothing below.
 *
 * The offset comes from [TextLayoutResult.firstBaseline], measured, rather than from an assumed
 * ascent. Deriving it from `hhea.ascent` looked right and was not: it put the alarm tiles' day
 * letters about 5 dp below the switch they were supposed to line up with, because whatever
 * `includeFontPadding = false` actually uses for the first line's top is not that number. A
 * measured baseline needs no assumption at all, since ink top is simply `baseline − capHeight`.
 */
@Composable
private fun InkTight(text: String, style: TextStyle, capHeight: Dp, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val layout = remember(text, style) { measurer.measure(text, style) }
    Layout(
        content = { BasicText(text = text, style = style, maxLines = 1, softWrap = false) },
        modifier = modifier,
    ) { measurables, _ ->
        // Unbounded: a text box is taller than its ink, and handing it the reported height would
        // squeeze the glyphs into half the space they need.
        val placeable = measurables.first().measure(
            Constraints(maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity),
        )
        val inkTop = (layout.firstBaseline - capHeight.toPx()).toInt()
        layout(placeable.width, capHeight.roundToPx()) { placeable.place(0, -inkTop) }
    }
}
