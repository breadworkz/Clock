package app.materialclock.data

/**
 * The five starting points the widget editor opens on, plus "Custom" as the sixth tile.
 *
 * A preset is a whole [WidgetConfig], not a hint; picking one replaces every field. That is what
 * makes the grid honest: the tile is rendered by the same code that will draw the widget, so what
 * is on the tile is exactly what lands on the home screen.
 *
 * They are chosen to span the axes rather than to be five variations of a circle: between them they
 * cover four face shapes, all three numeral treatments plus none, all four mark styles plus none,
 * and six of the eleven hand styles. Anything a preset does not reach is one tap away in Custom.
 */
enum class WidgetPreset(val label: String, val config: WidgetConfig) {

    /** The default: a filled circle, four numerals, dots between them. */
    CLASSIC(
        "Classic",
        WidgetConfig(),
    ),

    /** No container at all: a hairline ring, two needles, and a disc for the seconds. */
    BARE(
        "Bare",
        WidgetConfig(
            faceFill = FaceFill.NONE,
            outline = OutlineStyle.THIN,
            hourHand = HandStyle.NEEDLE,
            minuteHand = HandStyle.NEEDLE,
            secondHand = HandStyle.DOT,
            numerals = NumeralSystem.NONE,
            indexSet = IndexSet.NONE,
            minorIndices = MinorIndex.NONE,
        ),
    ),

    /** Twelve Roman numerals turned with the outline, the way a dial has always set them. */
    ROMAN(
        "Roman",
        WidgetConfig(
            shape = FaceShape.SQUIRCLE,
            hourHand = HandStyle.TAPER,
            minuteHand = HandStyle.TAPER,
            numerals = NumeralSystem.ROMAN,
            indexSet = IndexSet.ALL_TWELVE,
            minorIndices = MinorIndex.NONE,
            numeralSize = NumeralSize.SMALL,
            // Upright, not turned. Turning is right for a bezel of digits, but a Roman numeral
            // turned onto its side stops being read as a number at all. `III` at three o'clock
            // becomes three stacked dashes.
            numeralLayout = NumeralLayout.SHAPE_UPRIGHT,
            numeralWeight = NumeralWeight.LIGHT,
        ),
    ),

    /** Sixty ticks and arrowheads: no numerals, and none needed. */
    RAILWAY(
        "Railway",
        WidgetConfig(
            shape = FaceShape.ROUNDED_SQUARE,
            hourHand = HandStyle.ARROW,
            minuteHand = HandStyle.ARROW,
            secondHand = HandStyle.BALL,
            numerals = NumeralSystem.NONE,
            indexSet = IndexSet.NONE,
            minorIndices = MinorIndex.TICKS_60,
        ),
    ),

    /** The expressive one: a flower outline, open rings for the hours, leaf hands. */
    BLOOM(
        "Bloom",
        WidgetConfig(
            shape = FaceShape.FLOWER,
            hourHand = HandStyle.LEAF,
            minuteHand = HandStyle.LEAF,
            numerals = NumeralSystem.NONE,
            indexSet = IndexSet.NONE,
            minorIndices = MinorIndex.RINGS,
        ),
    ),
    ;

    companion object {
        /** The preset [config] is exactly, or null when the configuration has been hand-edited. */
        fun matching(config: WidgetConfig): WidgetPreset? = entries.firstOrNull { it.config == config }
    }
}
