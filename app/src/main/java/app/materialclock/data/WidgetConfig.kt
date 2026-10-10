package app.materialclock.data

import app.materialclock.ui.theme.Palette

/**
 * One placed widget's configuration, keyed by its `appWidgetId`.
 *
 * Every field has a default that produces a working clock. A record written by a newer build, or a
 * corrupt one, has to degrade to a plain circle inside a `BroadcastReceiver`. Throwing would leave
 * a dead rectangle on the home screen that the user has no way to fix.
 */
data class WidgetConfig(
    /* ── Face ───────────────────────────────────────────────────────────────────────────── */
    val shape: FaceShape = FaceShape.CIRCLE,
    /** Only read when [shape] is [FaceShape.PILL]. */
    val pillOrientation: PillOrientation = PillOrientation.HORIZONTAL,
    val fit: FitMode = FitMode.STRETCH,
    val faceFill: FaceFill = FaceFill.CONTAINER,
    val outline: OutlineStyle = OutlineStyle.NONE,

    /* ── Hands ──────────────────────────────────────────────────────────────────────────── */
    val hourHand: HandStyle = HandStyle.BATON,
    val minuteHand: HandStyle = HandStyle.BATON,
    val secondHand: HandStyle = HandStyle.OFF,
    val centrePin: Boolean = true,

    /* ── Indices ────────────────────────────────────────────────────────────────────────── */
    val numerals: NumeralSystem = NumeralSystem.ARABIC,
    val indexSet: IndexSet = IndexSet.QUARTERS,
    val minorIndices: MinorIndex = MinorIndex.DOTS,
    val numeralSize: NumeralSize = NumeralSize.MEDIUM,
    val numeralLayout: NumeralLayout = NumeralLayout.CIRCLE_UPRIGHT,
    val numeralWidth: NumeralWidth = NumeralWidth.NORMAL,
    val numeralWeight: NumeralWeight = NumeralWeight.MEDIUM,
    val numeralRound: NumeralRound = NumeralRound.NONE,

    /* ── Date ───────────────────────────────────────────────────────────────────────────── */
    val date: DateMode = DateMode.NONE,
    val datePosition: DatePosition = DatePosition.FOUR_THIRTY,

    /* ── Colour ─────────────────────────────────────────────────────────────────────────── */
    val colour: ColourSource = ColourSource.FOLLOW_APP,
    /** Only read when [colour] is [ColourSource.PALETTE]. */
    val palette: Palette = Palette.CONCEPT,
)

/**
 * The face outline.
 *
 * The first four are **parametric**: rebuilt at the widget's own aspect ratio, so their corners
 * stay circular in a box that is not square. The rest are Material's baked point lists, fitted by
 * `ShapeFit.fillRect`, which necessarily turns circular corners into ellipses when the widget is
 * not square. That is not a defect to fix: a fixed point list cannot both fill a non-square box and
 * keep its roundings circular, which is exactly why the four common shapes are parametric and why
 * [FitMode.UNIFORM] exists.
 *
 * [MATERIAL_PILL] is Material's own `Pill`, and it is *not* a stadium; measured, it is a
 * 45°-tilted capsule on a square bounding box. It is offered under an honest name; real pills come
 * from [PILL], which is built from `RoundedPolygon.pill`.
 */
enum class FaceShape(val label: String, val parametric: Boolean = false) {
    CIRCLE("Circle", true),
    ROUNDED_SQUARE("Rounded square", true),
    SQUARE_SHARP("Square", true),
    PILL("Pill", true),

    ARCH("Arch"),
    ARROW("Arrow"),
    BOOM("Boom"),
    BUN("Bun"),
    BURST("Burst"),
    CLAM_SHELL("Clamshell"),
    CLOVER_4("Clover, 4 leaf"),
    CLOVER_8("Clover, 8 leaf"),
    COOKIE_4("Cookie, 4 sided"),
    COOKIE_6("Cookie, 6 sided"),
    COOKIE_7("Cookie, 7 sided"),
    COOKIE_9("Cookie, 9 sided"),
    COOKIE_12("Cookie, 12 sided"),
    DIAMOND("Diamond"),
    FAN("Fan"),
    FLOWER("Flower"),
    GEM("Gem"),
    GHOSTISH("Ghost"),
    HEART("Heart"),
    MATERIAL_PILL("Tilted capsule"),
    OVAL("Oval"),
    PENTAGON("Pentagon"),
    PIXEL_CIRCLE("Pixel circle"),
    PIXEL_TRIANGLE("Pixel triangle"),
    PUFFY("Puffy"),
    PUFFY_DIAMOND("Puffy diamond"),
    SEMI_CIRCLE("Semicircle"),
    SLANTED("Slanted"),
    SOFT_BOOM("Soft boom"),
    SOFT_BURST("Soft burst"),
    SQUIRCLE("Squircle"),
    SUNNY("Sunny"),
    TRIANGLE("Triangle"),
    VERY_SUNNY("Very sunny"),
}

/**
 * [DIAGONAL] is the *inscribed* diagonal stadium: it touches all four edges with no distortion at
 * all, because it is built by rotating a true pill rather than by stretching one.
 *
 * Its angle is `atan2(H/2 − r, W/2 − r)`, which on a wide widget is 12–18°, not 45°. That is not an
 * approximation. A 45° stadium's bounding box is always square, so "45° *and* fills a non-square
 * box" is geometrically impossible. The stretched impression of one is [FaceShape.MATERIAL_PILL].
 */
enum class PillOrientation(val label: String) {
    HORIZONTAL("Across"),
    VERTICAL("Down"),
    DIAGONAL("Diagonal"),
}

/**
 * Labels are terse on purpose: these sit in a connected button group where three or four share the
 * screen's width, and anything much longer ellipsises into uselessness.
 */
enum class FitMode(val label: String) {
    /** Independent x and y scale onto the exact ink bounds. Always fills; distorts by sx/sy. */
    STRETCH("Stretch"),
    /** One scale for both axes, centred. Corners stay circular; leaves a gap on one axis. */
    UNIFORM("Uniform"),
}

/**
 * Whether the face carries a filled container behind the dial.
 *
 * Two values, not three. An earlier `OUTLINE_ONLY` did exactly what [NONE] does, because the
 * stroke has always come from [OutlineStyle], which is an independent control. That made it a
 * third button that changed nothing. An outlined face is `NONE` plus a stroke.
 */
enum class FaceFill(val label: String) {
    NONE("None"),
    CONTAINER("Filled"),
}

/** Stroke width as a fraction of the face's minimum radius. */
enum class OutlineStyle(val label: String, val widthR: Float) {
    NONE("None", 0f),
    HAIRLINE("Hairline", 0.008f),
    THIN("Thin", 0.016f),
    THICK("Thick", 0.032f),
}

/**
 * A hand's shape, or its absence.
 *
 * [OFF] for the hour and minute hands is drawn as a fully transparent icon rather than left unset:
 * `AnalogClock.setHourHand`/`setMinuteHand` are `@NonNull` and `onDraw` dereferences them without
 * checking. Only the second hand may legally be null, and passing null there is also what stops
 * the host's 1 Hz tick, which is the behaviour wanted.
 */
enum class HandStyle(val label: String) {
    OFF("Off"),
    BATON("Baton"),
    TAPER("Tapered"),
    NEEDLE("Needle"),
    OUTLINE("Outlined"),
    DIAMOND("Diamond"),
    /** A hairline with a disc near the tip: the counterpoise seconds hand of a chronograph. */
    BALL("Ball"),
    /** Disc at the very tip, no overhang: the Braun/Rams seconds hand. */
    DOT("Dot"),
    /** A stick that stops short, with an open ring at the end. */
    RING("Ring"),
    /** Pointed leaf, widest near the middle (the dress-watch hand). */
    LEAF("Leaf"),
    /** Straight stick with a triangular arrowhead. */
    ARROW("Arrow"),
}

enum class NumeralSystem(val label: String) {
    ARABIC("1 2 3"),
    /** Dial convention: `IIII` at four, `IX` at nine. Composed from ASCII I/V/X. */
    ROMAN("I II III"),
    /** U+0660–0669, from the bundled Noto Sans Arabic subset. */
    EASTERN_ARABIC("١ ٢ ٣"),
    NONE("None"),
}

enum class IndexSet(val label: String, val hours: Set<Int>) {
    ALL_TWELVE("All", (1..12).toSet()),
    QUARTERS("Quarters", setOf(12, 3, 6, 9)),
    TWELVE_ONLY("12", setOf(12)),
    NONE("None", emptySet()),
}

/**
 * What is drawn at the hour positions [IndexSet] leaves without a numeral.
 *
 * [NONE] means **nothing at all**, not "the default" and not "dots". It is a separate control from
 * the numerals on purpose, so a dial can have four numerals and eight dots, or twelve dots and no
 * numerals; but every "None" in this editor now leaves the dial genuinely bare.
 */
enum class MinorIndex(val label: String) {
    NONE("None"),
    /** Small filled discs. */
    DOTS("Dots"),
    /** Open circles. The same rhythm, a lighter dial. */
    RINGS("Rings"),
    TICKS("Ticks"),
    TICKS_60("60 ticks"),
}

/** Cap height as a fraction of the face's minimum radius. */
enum class NumeralSize(val label: String, val capR: Float) {
    SMALL("Small", 0.10f),
    MEDIUM("Medium", 0.15f),
    LARGE("Large", 0.22f),
}

enum class NumeralLayout(val label: String) {
    /** A true circle inside the shape, glyphs upright. Reads as a watch whatever the outline. */
    CIRCLE_UPRIGHT("Circle"),
    /** Riding the outline at a constant distance from it, glyphs still upright. */
    SHAPE_UPRIGHT("Shape"),
    /**
     * Riding the outline and turned with it, flipped through the lower half so nothing is upside
     * down, which is the traditional Roman treatment.
     */
    SHAPE_ROTATED("Turned"),
}

/**
 * The `wdth` axis, 25–151 on Google Sans Flex.
 *
 * Real width, not a horizontal scale: the axis was drawn, so stems stay their proper thickness
 * instead of being squashed with everything else. No effect on Eastern Arabic (the bundled Noto
 * subset carries `wght` only), so that control is hidden when those numerals are selected.
 */
enum class NumeralWidth(val label: String, val axis: Float) {
    CONDENSED("Tall", 25f),
    NARROW("Narrow", 65f),
    NORMAL("Normal", 100f),
    WIDE("Wide", 125f),
    EXTRA_WIDE("Widest", 151f),
}

/** The `wght` axis. The one axis both bundled fonts share. */
enum class NumeralWeight(val label: String, val axis: Int) {
    THIN("Thin", 150),
    LIGHT("Light", 300),
    MEDIUM("Medium", 500),
    BOLD("Bold", 700),
    BLACK("Black", 900),
}

/** The `ROND` axis, 0–100: how far the terminals round off toward a geometric sans. */
enum class NumeralRound(val label: String, val axis: Float) {
    NONE("Sharp", 0f),
    SOFT("Soft", 50f),
    FULL("Round", 100f),
}

enum class DateMode(val label: String) {
    NONE("None"),
    /** Just the day, the way a real watch aperture shows it: "7", never "07". */
    DAY("Day"),
    DAY_WEEKDAY("Weekday"),
    FULL("Full"),
}

/** Ignored for [DateMode.FULL], which is always centred below the pin. */
enum class DatePosition(val label: String) {
    THREE("3"),
    /** Between two indices, so it costs no numeral, which is why watches put it here. */
    FOUR_THIRTY("4:30"),
    SIX("6"),
}

enum class ColourSource(val label: String) {
    /** Follows the app's own theme, live: change the palette in Settings and the widget follows. */
    FOLLOW_APP("App"),
    DYNAMIC("Wallpaper"),
    PALETTE("Palette"),
}
