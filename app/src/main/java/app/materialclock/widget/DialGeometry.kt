package app.materialclock.widget

import app.materialclock.data.IndexSet
import app.materialclock.data.NumeralSize

/**
 * Every proportion on the dial, as a fraction of **Rmin**, the *smallest* distance from the centre
 * to the outline over a full turn.
 *
 * Rmin rather than the widget's half-width is the whole trick for arbitrary shapes: scale the hands
 * off it and a hand can never poke out of a clover, an arrow or a semicircle, whatever direction it
 * points. Numerals riding the outline are free to sit further out in the wide directions, because
 * they are placed from the outline itself rather than from a radius.
 *
 * The hand figures are Google's canonical watch-face geometry (the 450 × 450 Watch Face Format
 * reference dial, `w=20 h=190 pivotY=0.921` for the hour hand and so on), converted to fractions.
 * The pin and cell figures come from `ClockDialSelectorCenterContainerSize` (8/256) and
 * `ClockDialSelectorHandleContainerSize` (48/256) in Material's own time-picker tokens.
 */
object DialGeometry {

    /* ── Hands, as fractions of Rmin ────────────────────────────────────────────────────── */
    const val HOUR_LENGTH = 0.778f
    const val MINUTE_LENGTH = 0.889f
    const val SECOND_LENGTH = 0.933f

    const val HOUR_WIDTH = 0.0889f
    const val MINUTE_WIDTH = 0.0711f
    const val SECOND_WIDTH = 0.0356f

    /** The tail behind the pin. The thinnest hand carries the longest one, as on a real watch. */
    const val HOUR_TAIL = 0.067f
    const val MINUTE_TAIL = 0.0889f
    const val SECOND_TAIL = 0.156f

    const val PIN_DIAMETER = 0.0625f

    /* ── Indices ────────────────────────────────────────────────────────────────────────── */
    /**
     * The circle numerals sit on by default.
     *
     * This is the numeral's *centre*, so half its ink sits outside it: at the old 0.889 (the
     * minute hand's tip), a medium numeral reached 0.964 R and all but touched the outline. 0.82
     * puts the same numeral's outer edge at 0.895 R and leaves the dial a visible margin.
     */
    const val NUMERAL_RING = 0.82f
    /**
     * Dot **radius**, not diameter; the old name was wrong and the value with it. At 0.016 R a dot
     * on a 200 dp widget was 3 px across and read as a rendering artefact rather than as an index.
     */
    const val MINOR_DOT_RADIUS = 0.028f
    const val TICK_LENGTH = 0.055f
    const val TICK_LENGTH_MINOR = 0.030f

    /**
     * The smallest ring radius that fits twelve numerals without collision.
     *
     * Twelve cells of diameter `0.375 Rmin` (Material's own dial handle size) need an arc spacing
     * of at least that much: `2πr/12 = 0.524 r ≥ 0.375 R`, so `r ≥ 0.716 R`.
     */
    const val MIN_RING_FOR_TWELVE = 0.716f

    /**
     * The tangential room one numeral gets, in units of Rmin. A glyph must fit inside it.
     *
     * Four numerals instead of twelve is three times the arc each, which is why "large" numerals
     * and [IndexSet.QUARTERS] belong together and why "large" with all twelve has to be clamped.
     */
    fun arcSpacing(indexSet: IndexSet, ringR: Float): Float {
        val n = indexSet.hours.size.coerceAtLeast(1)
        return (2.0 * Math.PI * ringR / n).toFloat()
    }

    /**
     * The cap height to actually use, in units of Rmin.
     *
     * The requested size is a ceiling, not a promise. Roman numerals are the case that forces this:
     * `VIII` is four glyphs and roughly twice the tangential width of a single Arabic digit, so
     * "large" with all twelve indices would overlap. [widestInkR] is the measured ink width of the
     * widest string at cap height 1, so the clamp is against real glyphs rather than a guess.
     */
    fun clampedCap(size: NumeralSize, indexSet: IndexSet, ringR: Float, widestInkR: Float): Float {
        if (widestInkR <= 0f) return size.capR
        // Leave a tenth of the arc as breathing room between neighbours.
        val room = arcSpacing(indexSet, ringR) * 0.9f
        return minOf(size.capR, room / widestInkR)
    }
}
