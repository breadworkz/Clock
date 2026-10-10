#!/usr/bin/env python3
"""Regenerates `app/src/main/java/app/materialclock/ui/theme/Palettes.kt`.

Dynamic colour only exists from API 31 and this app supports API 26, so static schemes are needed
regardless. They also double as the theme picker's contents.

**Every scheme here is the Expressive variant.** Material ships nine scheme variants (tonal spot,
neutral, vibrant, fidelity, content, rainbow, fruit salad, monochrome, expressive); this app offers
only `SchemeExpressive`, because the whole app is a reconstruction of the M3 Expressive research
concept and mixing in a tonal-spot palette would quietly undo that. Expressive is the variant that
rotates the secondary and tertiary hues away from the seed instead of keeping them near it, which
is what gives these palettes the three genuinely different accents the alarm grid and the timer
ring both lean on.

The first entry, Concept, additionally pins three families to values measured off the published
renders: a scheme derived from purple cannot produce the coral of an armed alarm, and that coral
carries most of the concept's identity.

Requires: pip install materialyoucolor
Usage:    python3 tools/gen_palettes.py
"""

import pathlib

from materialyoucolor.dynamiccolor.material_dynamic_colors import MaterialDynamicColors as M
from materialyoucolor.hct import Hct
from materialyoucolor.scheme.scheme_expressive import SchemeExpressive

OUT = pathlib.Path(__file__).parent.parent / (
    "app/src/main/java/app/materialclock/ui/theme/Palettes.kt"
)

ROLES = [
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
    "surfaceTint", "inverseSurface", "inverseOnSurface",
    "error", "onError", "errorContainer", "onErrorContainer",
    "outline", "outlineVariant", "scrim",
    "surfaceBright", "surfaceDim", "surfaceContainer", "surfaceContainerHigh",
    "surfaceContainerHighest", "surfaceContainerLow", "surfaceContainerLowest",
    "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
    "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
    "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant",
]

# Sampled from `../clock-mockups/reference/concept/`.
CONCEPT_DARK_PINS = {
    "background": "1C0424",              # the Alarms ground
    "surface": "1C0424",
    "surfaceDim": "1C0424",
    "surfaceContainerLowest": "160320",
    "surfaceContainerLow": "230830",
    "surfaceContainer": "2B0C36",        # an alarm tile, a world-clock row
    "surfaceContainerHigh": "351344",
    "surfaceContainerHighest": "421B52",  # the analog dial face
    "tertiary": "FB7B88",                # the coral, i.e. "armed"
    "onTertiary": "39010E",
    "tertiaryContainer": "FB7B88",
    "onTertiaryContainer": "39010E",
    "primary": "E598FE",                 # the orchid on the dial's city capsules
    "outlineVariant": "4A2A57",
}
CONCEPT_LIGHT_PINS = {
    "surface": "F8F2FB",                 # the Plank timer's ground
    "background": "F8F2FB",
    "primary": "6750A4",
    "primaryContainer": "EADEFF",        # the Pause pill
    "onPrimaryContainer": "21005D",
    "tertiary": "B3263E",
    "tertiaryContainer": "FB7B88",
    "onTertiaryContainer": "39010E",
}

# (kotlin id, display name, seed, light pins, dark pins). Seeds walk the hue wheel so the picker
# offers genuinely different schemes rather than eight shades of one.
PALETTES = [
    ("CONCEPT", "Concept", 0xFF6750A4, CONCEPT_LIGHT_PINS, CONCEPT_DARK_PINS),
    ("VIOLET", "Violet", 0xFF6750A4, {}, {}),
    ("CORAL", "Coral", 0xFFFB7B88, {}, {}),
    ("EMBER", "Ember", 0xFFE8590C, {}, {}),
    ("CITRON", "Citron", 0xFFB5C334, {}, {}),
    ("FOREST", "Forest", 0xFF2E7D4F, {}, {}),
    ("LAGOON", "Lagoon", 0xFF00A2A7, {}, {}),
    ("AZURE", "Azure", 0xFF2C6BED, {}, {}),
    ("FUCHSIA", "Fuchsia", 0xFFD81B7C, {}, {}),
]

HEADER = '''package app.materialclock.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * GENERATED. Do not hand-edit. Regenerate with `tools/gen_palettes.py`.
 *
 * Every scheme below is Material's **Expressive** variant, and deliberately only that one. Of the
 * nine variants the colour system ships, Expressive is the one that rotates secondary and tertiary
 * well away from the seed hue instead of keeping them adjacent. That is what gives each palette
 * three accents different enough to carry the alarm grid, the ring and the switches apart.
 * Offering a tonal-spot or neutral option would hand the user a way to quietly turn the expressive
 * reconstruction back into an ordinary app.
 *
 * [Palette.CONCEPT] additionally pins the coral of an armed alarm and the near-black purple ground
 * to values measured off the published renders, because a scheme derived from purple cannot
 * produce them and they carry most of the concept's identity.
 *
 * The first two entries are not generated. They are the only two colour schemes the Material 3
 * Expressive documentation actually publishes as fixed values:
 *
 *  - **Baseline** is the static baseline scheme, `lightColorScheme()` / `darkColorScheme()`, whose
 *    every hex is listed in `styles/color/static/baseline.md`.
 *  - **Expressive** is `expressiveLightColorScheme()`, which *is* the expressive theme's own
 *    scheme. Checked against the shipped bytecode: it is `lightColorScheme()` with exactly four
 *    overrides, `onPrimaryContainer` / `onSecondaryContainer` / `onTertiaryContainer` /
 *    `onErrorContainer` moved from tone 10 to tone 30 (the Aug-2024 "more colorful text and icons"
 *    change). There is no `expressiveDarkColorScheme()` at all; `MaterialExpressiveTheme` uses the
 *    plain dark scheme, so this entry does too. **The expressive update changes nothing else about
 *    colour at the scheme level.** Everything after these two is generated by seeding the
 *    *expressive scheme variant*, which is where the rest of "expressive colour" actually lives.
 */
enum class Palette(val displayName: String, val light: ColorScheme, val dark: ColorScheme) {
'''


def scheme(seed: int, dark: bool, pins: dict, indent: str = "        ") -> str:
    s = SchemeExpressive(Hct.from_int(seed), dark, 0.0)
    lines = []
    for role in ROLES:
        if role in pins:
            hexv = pins[role]
        else:
            argb = getattr(M, role).get_argb(s)
            hexv = "%02X%02X%02X" % ((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF)
        lines.append(f"{indent}{role} = Color(0xFF{hexv})")
    return ",\n".join(lines)


def main() -> None:
    out = [HEADER]
    out.append(
        '    /** The static baseline scheme, verbatim. Every hex is published in the spec. */\n'
        '    BASELINE("Baseline", lightColorScheme(), darkColorScheme()),\n'
        '\n'
        '    /**\n'
        "     * The expressive theme's own scheme: baseline with four container-text roles lifted from\n"
        '     * tone 10 to tone 30. There is no expressive *dark* scheme in the library, so dark is the\n'
        '     * plain one. That is exactly what `MaterialExpressiveTheme` itself falls back to.\n'
        '     */\n'
        '    EXPRESSIVE("Expressive", expressiveLightColorScheme(), darkColorScheme()),\n'
        '\n'
    )
    for i, (kid, name, seed, lp, dp) in enumerate(PALETTES):
        tail = "," if i < len(PALETTES) - 1 else ";"
        out.append(f'    {kid}(\n        "{name}",\n')
        out.append(f"        lightColorScheme(\n{scheme(seed, False, lp)},\n        ),\n")
        out.append(f"        darkColorScheme(\n{scheme(seed, True, dp)},\n        ),\n")
        out.append(f"    ){tail}\n")
    out.append("}\n")
    out.append("""
/** The two schemes the concept itself was drawn in, kept named for the places that reference them. */
internal val ConceptLight: ColorScheme = Palette.CONCEPT.light
internal val ConceptDark: ColorScheme = Palette.CONCEPT.dark
""")
    body = "".join(out)
    OUT.write_text(body)
    print(f"→ {OUT.name}  {len(PALETTES)} palettes x 2 x {len(ROLES)} roles, {len(body)/1024:.1f} KB")


if __name__ == "__main__":
    main()
