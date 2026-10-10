package app.materialclock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.materialclock.R

/**
 * The Material 3 Expressive type scale, driven off a single bundled variable font.
 *
 * ## Why one font and not two
 *
 * The published M3 type scale names **two** families: `Google Sans` for Display, Headline and
 * Title-Large, and `Google Sans Text` for the other nine of fifteen styles (every piece of body
 * copy, every list item, every button label). **Google Sans Text is not licensed for third-party
 * use.** It carries no license grant in its binary and does not appear in the Google Fonts
 * catalogue; `fonts.googleapis.com` will serve it if asked by name, but being served is not being
 * licensed. That endpoint exists for Google's own properties.
 *
 * Google Sans Flex *is* OFL-1.1, and the Sans/Sans-Text split is fundamentally an optical-size
 * distinction. The spec's own tokens set `opsz` equal to the font size for all fifteen styles, so
 * driving the `opsz` axis reproduces both roles exactly rather than approximating them. That is
 * what this file does, and it is why every style below carries an explicit optical size.
 *
 * Full evidence: `docs/reference/18-google-fonts-licensing-verified.md` in the parent suite.
 *
 * The clock's own numerals are **not** set in this font: they are drawn geometry, because no
 * licensable face is narrow enough. See `ui/digits/ClockDigits.kt`. Everything else (titles,
 * labels, city names) is Google Sans Flex.
 *
 * ## Where the numbers come from
 *
 * Sizes and line heights are identical in both sources and are not in dispute. Tracking is taken
 * from the **published Google Sans scale**, not from `androidx.compose.material3.tokens`
 * `TypeScaleTokens` (v0_103), because those values are tuned for Roboto, adding up to +0.5sp of
 * letter-spacing at small sizes to compensate for a typeface we are not shipping. With a correct
 * `opsz` the optical-size axis already does that work; adding Roboto's tracking on top would
 * over-space the whole UI.
 *
 * Emphasized weights (Medium for display/headline/title-large, Bold for the rest) do come from the
 * androidx tokens, and agree with the spec's note that the emphasized set "differs mainly in wght".
 *
 * Source table: `docs/reference/98-type-scale-tokens-parsed.md`.
 */

/** Weights we instantiate per optical size. Enough that a call-site `fontWeight` override picks a
 *  real cut instead of triggering synthetic bold. */
private val INSTANTIATED_WEIGHTS = intArrayOf(400, 500, 600, 700)

private val familyCache = HashMap<Int, FontFamily>()

/**
 * A [FontFamily] of Google Sans Flex pinned to one optical size.
 *
 * `opsz` is clamped to the axis range published for the font (6–144); the merged binary we bundle
 * exposes `opsz` 6..144 and `wght` 1..1000 and nothing else, which is exactly the axis set the
 * documented type scale needs, since every style sets `wdth`, `GRAD`, `ROND`, `slnt`, `CRSV`,
 * `FILL` and `HEXP` to their defaults.
 */
private fun googleSansFlex(opticalSize: Float): FontFamily = familyCache.getOrPut(opticalSize.toInt()) {
    val opsz = opticalSize.coerceIn(6f, 144f)
    FontFamily(
        INSTANTIATED_WEIGHTS.map { w ->
            Font(
                resId = R.font.google_sans_flex,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(w),
                    FontVariation.Setting("opsz", opsz),
                ),
            )
        },
    )
}

/** Builds one style with its optical size locked to its point size, per the spec's tokens. */
private fun style(
    size: Int,
    lineHeight: Int,
    weight: Int,
    tracking: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = googleSansFlex(size.toFloat()),
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

private val REGULAR = 400
private val MEDIUM = 500
private val BOLD = 700

fun clockTypography(): Typography = Typography().copy(
    // Display / Headline / Title-Large, which are the spec's "Google Sans" roles.
    displayLarge = style(57, 64, REGULAR),
    displayMedium = style(45, 52, REGULAR),
    displaySmall = style(36, 44, REGULAR),
    headlineLarge = style(32, 40, REGULAR),
    headlineMedium = style(28, 36, REGULAR),
    headlineSmall = style(24, 32, REGULAR),
    titleLarge = style(22, 28, REGULAR),

    // Title-Medium and below: the spec's "Google Sans Text" roles, reproduced via opsz.
    titleMedium = style(16, 24, MEDIUM),
    titleSmall = style(14, 20, MEDIUM),
    bodyLarge = style(16, 24, REGULAR),
    bodyMedium = style(14, 20, REGULAR),
    bodySmall = style(12, 16, REGULAR, tracking = 0.1),
    labelLarge = style(14, 20, MEDIUM),
    labelMedium = style(12, 16, MEDIUM, tracking = 0.1),
    labelSmall = style(11, 16, MEDIUM, tracking = 0.1),

    // [M3E-NEW] The emphasized scale is an expressive addition of 15 parallel styles whose job is
    // to create weight contrast without changing the layout, since size and line height are
    // unchanged. Reach for these to mark the one thing on a screen that matters most.
    displayLargeEmphasized = style(57, 64, MEDIUM),
    displayMediumEmphasized = style(45, 52, MEDIUM),
    displaySmallEmphasized = style(36, 44, MEDIUM),
    headlineLargeEmphasized = style(32, 40, MEDIUM),
    headlineMediumEmphasized = style(28, 36, MEDIUM),
    headlineSmallEmphasized = style(24, 32, MEDIUM),
    titleLargeEmphasized = style(22, 28, MEDIUM),
    titleMediumEmphasized = style(16, 24, BOLD),
    titleSmallEmphasized = style(14, 20, BOLD),
    bodyLargeEmphasized = style(16, 24, MEDIUM),
    bodyMediumEmphasized = style(14, 20, MEDIUM),
    bodySmallEmphasized = style(12, 16, MEDIUM, tracking = 0.1),
    labelLargeEmphasized = style(14, 20, BOLD),
    labelMediumEmphasized = style(12, 16, BOLD, tracking = 0.1),
    labelSmallEmphasized = style(11, 16, BOLD, tracking = 0.1),
)

