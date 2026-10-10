package app.materialclock.widget

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.materialclock.data.ClockSettings
import app.materialclock.data.ColourSource
import app.materialclock.data.DateMode
import app.materialclock.data.DatePosition
import app.materialclock.data.FaceFill
import app.materialclock.data.FaceShape
import app.materialclock.data.FitMode
import app.materialclock.data.HandStyle
import app.materialclock.data.IndexSet
import app.materialclock.data.MinorIndex
import app.materialclock.data.NumeralLayout
import app.materialclock.data.NumeralSize
import app.materialclock.data.NumeralRound
import app.materialclock.data.NumeralSystem
import app.materialclock.data.NumeralWeight
import app.materialclock.data.NumeralWidth
import app.materialclock.data.OutlineStyle
import app.materialclock.data.PillOrientation
import app.materialclock.data.WidgetConfig
import app.materialclock.data.WidgetPreset
import app.materialclock.ui.sheets.SwitchRow
import app.materialclock.ui.theme.Palette

/**
 * The widget editor.
 *
 * Built from the app's own settings rows, so it looks like the rest of the app rather than like a
 * separate utility. It also carries a **live preview** rendered by the very code that will draw
 * the widget. That matters more than it sounds: there are 38 shapes times three numeral systems
 * times three layouts, and no amount of naming makes "Clover, 8 leaf, Roman, follow the shape,
 * turned" predictable. Seeing it is the only honest way to choose it.
 */
@Composable
fun WidgetConfigScreen(
    initial: WidgetConfig,
    settings: ClockSettings,
    /** False when the widget already exists and this is a reconfigure, which renames the button. */
    isNew: Boolean = true,
    onCancel: () -> Unit,
    onSave: (WidgetConfig) -> Unit,
) {
    var config by remember(initial) { mutableStateOf(initial) }
    var custom by rememberSaveable { mutableStateOf(false) }

    // Back returns to the grid before it cancels the whole activity; otherwise a swipe out of the
    // editor throws away the configuration the user just spent a minute on.
    BackHandler(enabled = custom) { custom = false }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Text(
                if (custom) "Custom clock" else "Clock widget",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            )

            AnimatedContent(
                targetState = custom,
                modifier = Modifier.weight(1f),
                // Custom arrives from the right and the grid leaves to the left, so the pair reads
                // as one strip being slid across rather than two screens being swapped. Reversed on
                // the way back, which is what makes Back feel like the inverse of the tap.
                transitionSpec = {
                    val d = 320
                    if (targetState) {
                        slideInHorizontally(tween(d)) { it } togetherWith
                            slideOutHorizontally(tween(d)) { -it }
                    } else {
                        slideInHorizontally(tween(d)) { -it } togetherWith
                            slideOutHorizontally(tween(d)) { it }
                    }
                },
                label = "customSlide",
            ) { showCustom ->
            if (!showCustom) {
                PresetGrid(
                    config = config,
                    settings = settings,
                    onPreset = { config = it.config },
                    onCustom = { custom = true },
                )
            } else {
            Column {
            Preview(config, settings, Modifier.padding(horizontal = 24.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Shapes, hands and palettes are carousels of *rendered* thumbnails; everything
                // else is a connected button group. The cut is at five options, because below
                // that a carousel hides behind a scroll what one row would have shown outright.
                ShapeCarousel(config, settings) { config = config.copy(shape = it) }
                if (config.shape == FaceShape.PILL) {
                    OptionGroup(
                        "Orientation", config.pillOrientation, PillOrientation.entries,
                        { it.label },
                    ) { config = config.copy(pillOrientation = it) }
                }
                OptionGroup("Fit", config.fit, FitMode.entries, { it.label }) {
                    config = config.copy(fit = it)
                }
                OptionGroup("Background", config.faceFill, FaceFill.entries, { it.label }) {
                    config = config.copy(faceFill = it)
                }
                OptionGroup("Outline", config.outline, OutlineStyle.entries, { it.label }) {
                    config = config.copy(outline = it)
                }

                HandCarousel("Hour hand", config.hourHand, settings, config,
                    DialGeometry.HOUR_LENGTH, DialGeometry.HOUR_WIDTH, DialGeometry.HOUR_TAIL,
                    { it.hourHand }) {
                    config = config.copy(hourHand = it)
                }
                HandCarousel("Minute hand", config.minuteHand, settings, config,
                    DialGeometry.MINUTE_LENGTH, DialGeometry.MINUTE_WIDTH, DialGeometry.MINUTE_TAIL,
                    { it.minuteHand }) {
                    config = config.copy(minuteHand = it)
                }
                HandCarousel("Second hand", config.secondHand, settings, config,
                    DialGeometry.SECOND_LENGTH, DialGeometry.SECOND_WIDTH, DialGeometry.SECOND_TAIL,
                    { it.secondHand }) {
                    config = config.copy(secondHand = it)
                }
                if (config.secondHand != HandStyle.OFF) {
                    Note(
                        "Ticks once a second. Some devices switch the second hand off " +
                            "system-wide; if it never appears, yours is one of them.",
                    )
                }
                SwitchRow(
                    title = "Centre pin",
                    checked = config.centrePin,
                    onChange = { config = config.copy(centrePin = it) },
                )

                OptionGroup("Numerals", config.numerals, NumeralSystem.entries, { it.label }) {
                    config = config.copy(numerals = it)
                }
                // Everything below only describes numerals, so with "None" chosen it would be a
                // stack of controls that visibly do nothing. None means none, including here.
                if (config.numerals != NumeralSystem.NONE) {
                    OptionGroup("Which hours", config.indexSet, IndexSet.entries, { it.label }) {
                        config = config.copy(indexSet = it)
                    }
                }
                if (config.numerals != NumeralSystem.NONE && config.indexSet != IndexSet.NONE) {
                    OptionGroup("Number size", config.numeralSize, NumeralSize.entries, { it.label }) {
                        config = config.copy(numeralSize = it)
                    }
                    OptionGroup(
                        "Number placement", config.numeralLayout, NumeralLayout.entries, { it.label },
                    ) { config = config.copy(numeralLayout = it) }

                    // The font's own axes, shown as the glyphs they produce. Google Sans Flex has
                    // six; three of them change a numeral's shape in a way worth choosing between.
                    if (config.numerals != NumeralSystem.EASTERN_ARABIC) {
                        AxisCarousel(
                            "Number width", config.numeralWidth, NumeralWidth.entries,
                            { it.label }, settings, config,
                            { c, o -> c.copy(numeralWidth = o) },
                        ) { config = config.copy(numeralWidth = it) }
                    }
                    AxisCarousel(
                        "Number weight", config.numeralWeight, NumeralWeight.entries,
                        { it.label }, settings, config,
                        { c, o -> c.copy(numeralWeight = o) },
                    ) { config = config.copy(numeralWeight = it) }
                    if (config.numerals != NumeralSystem.EASTERN_ARABIC) {
                        OptionGroup(
                            "Number shape", config.numeralRound, NumeralRound.entries, { it.label },
                        ) { config = config.copy(numeralRound = it) }
                    } else {
                        Note(
                            "Eastern Arabic digits come from Noto Sans Arabic, which is variable " +
                                "on weight only. Width and shape do not apply.",
                        )
                    }
                }
                IndexCarousel(
                    label = if (config.numerals == NumeralSystem.NONE ||
                        config.indexSet == IndexSet.NONE
                    ) "Hour marks" else "Other hours",
                    value = config.minorIndices,
                    settings = settings,
                    config = config,
                ) { config = config.copy(minorIndices = it) }

                OptionGroup("Date", config.date, DateMode.entries, { it.label }) {
                    config = config.copy(date = it)
                }
                if (config.date != DateMode.NONE && config.date != DateMode.FULL) {
                    OptionGroup(
                        "Date position", config.datePosition, DatePosition.entries, { it.label },
                    ) { config = config.copy(datePosition = it) }
                }

                OptionGroup("Colour", config.colour, ColourSource.entries, { it.label }) {
                    config = config.copy(colour = it)
                }
                if (config.colour == ColourSource.PALETTE) {
                    PaletteCarousel(config.palette) { config = config.copy(palette = it) }
                }
                Spacer(Modifier.height(16.dp))
            }
            }
            }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { if (custom) custom = false else onCancel() },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(if (custom) "Back" else "Cancel")
                }
                Button(
                    onClick = { onSave(config) },
                    modifier = Modifier.weight(1.6f).height(56.dp),
                ) {
                    Text(if (isNew) "Add widget" else "Save")
                }
            }
        }
    }
}

/**
 * The screen the editor opens on: five whole configurations and a door to the rest.
 *
 * Every tile is drawn by [renderStill], the same code that renders the live preview and, modulo
 * the hands that the host ticks, the widget itself. Nothing here is an illustration of a preset.
 *
 * Selection is read from the configuration rather than held separately: a tile is on when [config]
 * equals it exactly, and **Custom** is on when it equals none of them. So editing anything in the
 * custom screen hands the selection to the Custom tile with no state to keep in sync.
 */
@Composable
private fun PresetGrid(
    config: WidgetConfig,
    settings: ClockSettings,
    onPreset: (WidgetPreset) -> Unit,
    onCustom: () -> Unit,
) {
    val matched = WidgetPreset.matching(config)
    val rows = (WidgetPreset.entries.map<WidgetPreset, Any> { it } + CUSTOM_TILE).chunked(2)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(TILE_GAP),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                row.forEach { cell ->
                    if (cell is WidgetPreset) {
                        PresetTile(
                            preset = cell,
                            selected = matched == cell,
                            settings = settings,
                            modifier = Modifier.weight(1f),
                            onClick = { onPreset(cell) },
                        )
                    } else {
                        CustomTile(
                            selected = matched == null,
                            modifier = Modifier.weight(1f),
                            onClick = onCustom,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private val TILE_GAP = 12.dp
private val TILE_CORNER = 28.dp
private val TILE_ASPECT = 1.28f
private const val CUSTOM_TILE = "custom"

@Composable
private fun PresetTile(
    preset: WidgetPreset,
    selected: Boolean,
    settings: ClockSettings,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bitmap = remember(preset, settings, density) {
        val w = with(density) { 320.dp.toPx() }.toInt()
        renderStill(context, preset.config, settings, w, (w / TILE_ASPECT).toInt())
    }
    TileFrame(selected = selected, label = preset.label, modifier = modifier, onClick = onClick) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(10.dp),
        )
    }
}

@Composable
private fun CustomTile(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TileFrame(
        selected = selected,
        label = "Custom",
        modifier = modifier,
        // A different container from the five, because it is a different kind of thing: the others
        // set the widget, this one opens a screen.
        container = MaterialTheme.colorScheme.tertiaryContainer,
        onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(30.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun TileFrame(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    // Deliberately *Low*, not High: the widget's own face is `surfaceContainerHigh`, so a tile at
    // that role renders the face invisible: Bloom's flower and Railway's rounded square simply did
    // not appear. The tile has to sit a step below whatever it is framing.
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onContainer: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(TILE_ASPECT)
                .clip(RoundedCornerShape(TILE_CORNER))
                .background(if (selected) scheme.primary else container)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (selected) scheme.onPrimary else onContainer,
            ) { content() }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * The face as it will actually appear, drawn by [FaceRenderer] itself.
 *
 * The hands are drawn on top here rather than left to `AnalogClock`, because in the editor there is
 * no host to tick them. The preview shows them at a fixed 10:09, the angle every watch advert uses
 * because it frames the dial without hiding it.
 */
@Composable
private fun Preview(config: WidgetConfig, settings: ClockSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bitmap: Bitmap = remember(config, settings, density) {
        // Twice the dp it is displayed at. The preview stretches to the screen's width, so
        // rendering at its nominal size upscales the bitmap and the hands come out visibly
        // stair-stepped, which is an artefact of the preview that the real widget never has.
        val w = with(density) { 440.dp.toPx() }.toInt()
        renderStill(context, config, settings, w, with(density) { 264.dp.toPx() }.toInt())
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(220f / 132f)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Every shape, drawn.
 *
 * The thumbnail is the real [facePath] at the cell's own aspect, so what the carousel shows is
 * literally what the widget will draw, including the way a stretched shape's corners go
 * elliptical, which is the one thing a name could never convey.
 */
@Composable
private fun ShapeCarousel(
    config: WidgetConfig,
    settings: ClockSettings,
    onSelect: (FaceShape) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    OptionCarousel(
        label = "Face",
        value = config.shape,
        options = FaceShape.entries,
        optionLabel = { it.label },
        itemLabels = false,
        onSelect = onSelect,
    ) { shape, selected ->
        // Inverted with the cell: accent shape on a neutral cell, neutral shape on an accent cell.
        val ink = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.primary
        }.toArgb()
        val bitmap = remember(shape, config.fit, config.pillOrientation, ink, density) {
            val px = with(density) { 62.dp.toPx() }.toInt()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val path = facePath(shape, config.pillOrientation, config.fit, px.toFloat(), px.toFloat())
            canvas.drawPath(
                path,
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).also { it.color = ink },
            )
            bmp
        }
        ThumbnailImage(bitmap, shape.label)
    }
}

/** Every hand style, drawn at the size and colour it will really be. */
@Composable
private fun HandCarousel(
    label: String,
    value: HandStyle,
    settings: ClockSettings,
    config: WidgetConfig,
    lengthR: Float,
    widthR: Float,
    tailR: Float,
    colour: (FaceRenderer.Colours) -> Int,
    onSelect: (HandStyle) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    OptionCarousel(
        label = label,
        value = value,
        options = HandStyle.entries,
        optionLabel = { it.label },
        onSelect = onSelect,
    ) { style, selected ->
        val colours = remember(settings, config.colour, config.palette) {
            WidgetColours.resolve(context, config, settings)
        }
        // Unselected shows the hand in the colour it will really be; this is the only place the
        // three hands' different roles are visible before the widget is placed. Selected inverts
        // to the cell's own ink, because nothing survives being drawn on top of `primary`.
        val ink = if (selected) MaterialTheme.colorScheme.onPrimary.toArgb() else colour(colours)
        val pin = if (selected) ink else colours.pin
        if (style == HandStyle.OFF) {
            Text(
                "Off",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        } else {
            val bitmap = remember(style, ink, pin, density) {
                val px = with(density) { 62.dp.toPx() }
                HandRenderer.render(
                    style, ink, px / 2f, lengthR, widthR, tailR, DialGeometry.PIN_DIAMETER, pin,
                )
            }
            ThumbnailImage(bitmap, style.label)
        }
    }
}

/** The palettes, as the three-accent gradients the app's own picker uses. */
@Composable
private fun PaletteCarousel(value: Palette, onSelect: (Palette) -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    OptionCarousel(
        label = "Palette",
        value = value,
        options = Palette.entries,
        optionLabel = { it.displayName },
        onSelect = onSelect,
    ) { palette, _ ->
        val scheme = if (dark) palette.dark else palette.light
        // The three accents, not one: the expressive variant rotates secondary and tertiary well
        // away from the seed, so a single dot would tell you almost nothing about the palette.
        Box(
            Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        0f to scheme.primary,
                        0.5f to scheme.tertiary,
                        1f to scheme.secondary,
                    )
                )
        )
    }
}

/**
 * The hour marks, drawn by the renderer that will draw them for real.
 *
 * "None" is a word, not a picture, because the honest thumbnail for it is an empty cell, and an
 * empty cell in a strip of drawn options reads as a failed render rather than as a choice.
 */
@Composable
private fun IndexCarousel(
    label: String,
    value: MinorIndex,
    settings: ClockSettings,
    config: WidgetConfig,
    onSelect: (MinorIndex) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    OptionCarousel(
        label = label,
        value = value,
        options = MinorIndex.entries,
        optionLabel = { it.label },
        onSelect = onSelect,
    ) { option, selected ->
        val onCell = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        if (option == MinorIndex.NONE) {
            Text("None", style = MaterialTheme.typography.labelLarge, color = onCell)
        } else {
            val ink = onCell.toArgb()
            val bitmap = remember(option, settings, config.colour, config.palette, ink, density) {
                val px = with(density) { 62.dp.toPx() }.toInt()
                // Stripped to nothing but the marks: no fill, no stroke, no numerals, no date. The
                // cell then shows the one thing being chosen.
                val probe = config.copy(
                    shape = FaceShape.CIRCLE,
                    faceFill = FaceFill.NONE,
                    outline = OutlineStyle.NONE,
                    numerals = NumeralSystem.NONE,
                    indexSet = IndexSet.NONE,
                    minorIndices = option,
                    date = DateMode.NONE,
                )
                val base = WidgetColours.resolve(context, probe, settings)
                FaceRenderer.render(
                    context, probe,
                    FaceRenderer.Colours(
                        face = base.face, onFace = ink, minor = ink, outline = ink, accent = ink,
                        hourHand = ink, minuteHand = ink, secondHand = ink, pin = ink,
                    ),
                    px, px,
                ).bitmap
            }
            ThumbnailImage(bitmap, option.label)
        }
    }
}

/**
 * One font axis, shown as the glyph it produces.
 *
 * A name cannot carry this. "Widest" is `wdth 151`, where a digit's advance is 1.109 em against a
 * 0.716 em cap (half again wider than it is tall), and no label conveys that. [apply] puts the
 * candidate value into a copy of the configuration so the cell renders through exactly the code
 * path the dial will use.
 */
@Composable
private fun <T> AxisCarousel(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    settings: ClockSettings,
    config: WidgetConfig,
    apply: (WidgetConfig, T) -> WidgetConfig,
    onSelect: (T) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    OptionCarousel(
        label = label,
        value = value,
        options = options,
        optionLabel = optionLabel,
        itemSize = 72.dp,
        onSelect = onSelect,
    ) { option, selected ->
        val ink = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }.toArgb()
        val bitmap = remember(option, config.numerals, ink, density) {
            val px = with(density) { 54.dp.toPx() }.toInt()
            numeralThumb(context, px, apply(config, option), ink)
        }
        ThumbnailImage(bitmap, optionLabel(option))
    }
}

/** "12" at one point in the font's axis space, ink-centred in a square. */
private fun numeralThumb(
    context: android.content.Context,
    px: Int,
    config: WidgetConfig,
    colour: Int,
): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val text = TextInk.label(12, config.numerals).ifEmpty { "12" }
    val paint = TextInk.numeralPaint(
        context, 100f,
        width = config.numeralWidth.axis,
        weight = config.numeralWeight.axis,
        roundness = config.numeralRound.axis,
        system = config.numerals,
    ).apply { color = colour }
    paint.textSize = TextInk.sizeForCap(paint, text, px * 0.52f)
    var ink = TextInk.ink(paint, text)
    // Cap height first, then shrink if the width axis has pushed the pair past the cell. Sizing on
    // height alone would run "12" at `wdth 151` straight off both sides.
    val room = px * 0.88f
    if (ink.width() > room) {
        paint.textSize *= room / ink.width()
        ink = TextInk.ink(paint, text)
    }
    canvas.drawText(
        text,
        px / 2f - ink.width() / 2f - ink.left,
        px / 2f + ink.height() / 2f - ink.bottom,
        paint,
    )
    return bmp
}

/** A line of explanation under a control, in the editor's own quiet register. */
@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}


/**
 * The whole clock, face *and* hands, drawn into one bitmap at a fixed 10:09.
 *
 * On the home screen the hands belong to the host's `AnalogClock` and only the face is ours. There
 * is no host here, so the editor draws them itself; 10:09 is the angle every watch advertisement
 * uses, because the two hands frame the dial instead of covering it.
 *
 * Shared by the live preview and every preset tile, so a tile can never drift from what the
 * configuration it represents actually produces.
 */
private fun renderStill(
    context: android.content.Context,
    config: WidgetConfig,
    settings: ClockSettings,
    w: Int,
    h: Int,
): Bitmap {
    val colours = WidgetColours.resolve(context, config, settings)
    val face = FaceRenderer.render(context, config, colours, w, h)
    val canvas = android.graphics.Canvas(face.bitmap)

    fun hand(style: HandStyle, colour: Int, lengthR: Float, widthR: Float, tailR: Float, minutes: Float) {
        if (style == HandStyle.OFF) return
        val bmp = HandRenderer.render(style, colour, face.rMin, lengthR, widthR, tailR, 0f, colours.pin)
        canvas.save()
        canvas.rotate(minutes * 6f, face.cx, face.cy)
        canvas.drawBitmap(bmp, face.cx - bmp.width / 2f, face.cy - bmp.height / 2f, null)
        canvas.restore()
    }
    hand(config.hourHand, colours.hourHand, DialGeometry.HOUR_LENGTH, DialGeometry.HOUR_WIDTH, DialGeometry.HOUR_TAIL, 50.75f)
    hand(config.minuteHand, colours.minuteHand, DialGeometry.MINUTE_LENGTH, DialGeometry.MINUTE_WIDTH, DialGeometry.MINUTE_TAIL, 9f)
    hand(config.secondHand, colours.secondHand, DialGeometry.SECOND_LENGTH, DialGeometry.SECOND_WIDTH, DialGeometry.SECOND_TAIL, 35f)
    if (config.centrePin) {
        canvas.drawCircle(
            face.cx, face.cy, DialGeometry.PIN_DIAMETER * face.rMin / 2f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).also { it.color = colours.pin },
        )
    }
    return face.bitmap
}
