package app.materialclock.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.materialclock.data.DarkMode
import app.materialclock.data.ThemeSettings

/**
 * The theme root.
 *
 * [MaterialExpressiveTheme] rather than `MaterialTheme` is the single most consequential line here:
 * it installs the expressive motion scheme, shapes and the emphasized type slots. It carries no
 * opt-in annotation in 1.5.0-alpha25; the theming layer went stable ahead of the components.
 *
 * ## One scheme for the whole app
 *
 * An earlier build ran Alarms and World Clock dark while Timers and Stopwatch were light, because
 * that is how the concept renders happened to be published. As a *reconstruction* that was
 * faithful; as an *app* it was indefensible. Light and dark are a user setting and an
 * accessibility one, and no four-tab app should flash white when you press the third tab at 3 a.m.
 * The concept's contrast is preserved instead through surface roles, which is where it belongs.
 *
 * ## Colour source
 *
 * Wallpaper colour is the default from API 31 up. Below that, or with it switched off, the app
 * falls back to one of the [Palette] entries, every one of them the Expressive scheme variant.
 */
@Composable
fun ClockTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    val context = LocalContext.current
    val base = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> settings.palette.dark
        else -> settings.palette.light
    }
    val amoled = dark && settings.amoledBlack
    val scheme = remember(base, amoled) { if (amoled) base.toAmoledBlack() else base }

    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(dark, activity) {
        val bar = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity?.enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
    }

    CompositionLocalProvider(
        // Without this, `Surface` composites `surfaceTint` over its container as elevation rises,
        // quietly painting grey back over a pure-black AMOLED background, which is the one thing
        // the setting exists to prevent.
        LocalTonalElevationEnabled provides !amoled,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            // [M3E-NEW] Under-damped spatial springs paired with critically damped effects
            // springs. Movement overshoots; colour never does. On a clock that asymmetry has more
            // to do than usual, since every screen here has something in continuous motion.
            motionScheme = MotionScheme.expressive(),
            typography = remember { clockTypography() },
            content = content,
        )
    }
}

/**
 * True-black variant of a dark scheme, for OLED panels.
 *
 * A clock is the app most likely to be showing on a dimmed screen at 3 a.m., which is exactly when
 * a near-black surface still glows. Accent roles and the upper containers are untouched: the alarm
 * tiles live on `surfaceContainer`, so blackening the page increases their separation rather than
 * flattening it.
 */
fun ColorScheme.toAmoledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF141018),
    scrim = Color.Black,
)
