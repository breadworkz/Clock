package app.materialclock.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.res.ResourcesCompat
import app.materialclock.R
import app.materialclock.data.NumeralSystem

/**
 * Text for the widget, built on `android.graphics.Paint` with no Compose anywhere.
 *
 * A widget's bitmap is rendered in a `BroadcastReceiver` or a config activity's background work,
 * so none of the app's `@Composable` type layer is reachable. What *is* reachable is everything in
 * `ClockFace` that is a plain constant or a plain function, and the technique its `rememberInkBounds`
 * uses: `Paint.getTextBounds` for ink, `Paint.measureText` for advance.
 */
object TextInk {

    /**
     * A paint pinned to a point in Google Sans Flex's axis space.
     *
     * `Paint.setFontVariationSettings` is API 26 and returns false when no underlying font carries
     * the axes. It is checked rather than assumed, because the failure is silent: numerals would
     * come out at normal width with no error anywhere.
     */
    fun numeralPaint(
        context: Context,
        sizePx: Float,
        width: Float = 100f,
        weight: Int = 500,
        roundness: Float = 0f,
        system: NumeralSystem = NumeralSystem.ARABIC,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        val font = when (system) {
            // Google Sans Flex has no Arabic coverage at all: the family is served in latin,
            // latin-ext, math, symbols, syriac, cherokee, nushu, tifinagh, canadian-aboriginal and
            // vietnamese, and nothing else. The Eastern Arabic digits come from a ten-glyph subset
            // of Noto Sans Arabic instead. This reference is also what stops `shrinkResources`
            // deleting that file from a release build.
            NumeralSystem.EASTERN_ARABIC -> R.font.noto_sans_arabic_digits
            else -> R.font.google_sans_flex
        }
        typeface = ResourcesCompat.getFont(context, font)
        textSize = sizePx
        textAlign = Paint.Align.LEFT
        if (system == NumeralSystem.EASTERN_ARABIC) {
            // Noto Sans Arabic is variable on `wght` only; there is no width axis, so a condensed
            // treatment cannot apply here and asking for one would silently do nothing.
            setFontVariationSettings("'wght' $weight")
        } else {
            fontFeatureSettings = "tnum, lnum"
            setFontVariationSettings(
                "'opsz' 144,'wdth' $width,'wght' $weight,'GRAD' 0,'ROND' $roundness"
            )
        }
    }

    /**
     * The size to set so a string's ink is exactly [capPx] tall.
     *
     * Measured rather than derived from a constant. `ClockFace.DIGIT_EM` is 0.728, but that is a
     * Google Sans Flex number: the Noto Arabic digits measure 0.691 em with `٨` descending 0.016 em
     * below the baseline. Using the wrong one makes one numeral system visibly smaller than the
     * others.
     */
    fun sizeForCap(paint: Paint, sample: String, capPx: Float): Float {
        val r = Rect()
        paint.textSize = 100f
        paint.getTextBounds(sample, 0, sample.length, r)
        val inkAt100 = r.height().toFloat().takeIf { it > 0f } ?: 72.8f
        return 100f * capPx / inkAt100
    }

    /** Ink bounds of [text] at the paint's current size, the box the glyphs actually occupy. */
    fun ink(paint: Paint, text: String, out: Rect = Rect()): Rect {
        paint.getTextBounds(text, 0, text.length, out)
        return out
    }

    /**
     * The label for an hour, in the chosen system.
     *
     * Roman uses the **watch-dial** convention: `IIII` at four, not `IV`. It is not a mistake and
     * it is not decoration. It balances `VIII` opposite it optically, and it makes the three
     * quadrants read `I II III IIII` / `V VI VII VIII` / `IX X XI XII`, additive all the way to X.
     * Built from ASCII I/V/X rather than the Unicode numeral forms at U+2160, which Google Sans
     * Flex does not contain and which have no precomposed `IIII` in any case.
     */
    fun label(hour: Int, system: NumeralSystem): String {
        val h = ((hour - 1).mod(12)) + 1
        return when (system) {
            NumeralSystem.NONE -> ""
            NumeralSystem.ARABIC -> h.toString()
            NumeralSystem.ROMAN -> ROMAN[h - 1]
            NumeralSystem.EASTERN_ARABIC -> h.toString().map { EASTERN[it - '0'] }.joinToString("")
        }
    }

    /** The widest string the system will ever put on a dial. The collision clamp measures it. */
    fun widest(system: NumeralSystem): String = when (system) {
        NumeralSystem.NONE -> ""
        NumeralSystem.ARABIC -> "12"
        NumeralSystem.ROMAN -> "VIII"
        NumeralSystem.EASTERN_ARABIC -> "١٢"
    }

    private val ROMAN = arrayOf(
        "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII",
    )

    /** U+0660 ARABIC-INDIC DIGIT ZERO … NINE. */
    private val EASTERN = CharArray(10) { (0x0660 + it).toChar() }
}
