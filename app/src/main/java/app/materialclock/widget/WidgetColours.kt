package app.materialclock.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import app.materialclock.data.ClockSettings
import app.materialclock.data.ColourSource
import app.materialclock.data.DarkMode
import app.materialclock.data.WidgetConfig
import app.materialclock.ui.theme.toAmoledBlack

/**
 * The widget's colours, resolved without Compose.
 *
 * `ColorScheme` is a plain data class (only the *theming* composable is a composable), so the same
 * `Palette` entries and the same `toAmoledBlack` transform the app uses are reachable from a
 * `BroadcastReceiver`. That matters: "match the app" has to mean the identical colours, not an
 * approximation of them.
 */
object WidgetColours {

    fun resolve(context: Context, config: WidgetConfig, appSettings: ClockSettings): FaceRenderer.Colours {
        val dark = when {
            config.colour == ColourSource.FOLLOW_APP -> when (appSettings.theme.darkMode) {
                DarkMode.SYSTEM -> systemDark(context)
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
            else -> systemDark(context)
        }

        val base: ColorScheme = when {
            config.colour == ColourSource.FOLLOW_APP -> {
                val t = appSettings.theme
                if (t.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamic(context, dark)
                } else if (dark) t.palette.dark else t.palette.light
            }
            config.colour == ColourSource.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                dynamic(context, dark)
            // Below API 31 "wallpaper colours" has nothing to read, so it falls back to the picked
            // palette rather than to grey.
            else -> if (dark) config.palette.dark else config.palette.light
        }

        val amoled = config.colour == ColourSource.FOLLOW_APP && appSettings.theme.amoledBlack
        val scheme = if (dark && amoled) base.toAmoledBlack() else base

        // Seven roles, not one accent repeated.
        //
        // Every one of these is drawn on the *neutral* surface family, and that is deliberate:
        // M3 guarantees contrast within a container/on-container pair and for the accent roles
        // against `surface`, but it guarantees nothing for tertiary-on-primaryContainer. So the
        // face stays a surface role, one that in a dynamic scheme is already wallpaper-tinted, and
        // the three hands are free to be genuinely different colours on top of it.
        //
        // The hands run primary → tertiary → secondary in that order, and the order is the point.
        // In a *dynamic* scheme `secondary` is a desaturated near-copy of `primary`, and putting
        // those two on the hour and minute hands makes the pair read as one colour. By contrast
        // `tertiary` is rotated well off the seed. So the two hands the eye actually reads take
        // primary and tertiary, and secondary goes to the second hand, which is a hairline in
        // motion and does not have to fight for separation.
        //
        // The minor indices drop to `onSurfaceVariant` so a dial with four numerals and eight dots
        // reads as two levels rather than twelve equal marks.
        return FaceRenderer.Colours(
            face = scheme.surfaceContainerHigh.toArgb(),
            onFace = scheme.onSurface.toArgb(),
            minor = scheme.onSurfaceVariant.toArgb(),
            outline = scheme.outlineVariant.toArgb(),
            accent = scheme.primary.toArgb(),
            hourHand = scheme.primary.toArgb(),
            minuteHand = scheme.tertiary.toArgb(),
            secondHand = scheme.secondary.toArgb(),
            // Neutral, because the pin sits on top of all three hands and matching any one of them
            // would make that hand look severed at the centre.
            pin = scheme.onSurface.toArgb(),
        )
    }

    private fun dynamic(context: Context, dark: Boolean): ColorScheme =
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    /**
     * The host's night mode, not the app's.
     *
     * A widget is drawn into the launcher's window, so it has to follow the *system's* mode even
     * when the app itself is pinned to light or dark. Otherwise a dark home screen carries a white
     * clock.
     */
    private fun systemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
