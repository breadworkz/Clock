package app.materialclock.ui.sheets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.materialclock.alarm.AlarmReceiver
import app.materialclock.alarm.AlarmRingActivity
import app.materialclock.alarm.AlarmService
import app.materialclock.data.ClockSettings
import app.materialclock.data.DarkMode
import app.materialclock.data.HourFormat
import app.materialclock.data.WeekStart
import app.materialclock.ui.theme.Palette
import app.materialclock.widget.ClockWidgetProvider
import app.materialclock.widget.WidgetEntryPoints

fun triggerTestAlarm(context: Context) {
    val serviceIntent = Intent(context, AlarmService::class.java).apply {
        action = AlarmService.ACTION_START
        putExtra(AlarmReceiver.EXTRA_ID, -1L)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
    } else {
        context.startService(serviceIntent)
    }

    val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
        putExtra(AlarmReceiver.EXTRA_ID, -1L)
        putExtra(AlarmRingActivity.EXTRA_LABEL, "Test Alarm")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(ringIntent)
}

/**
 * One sheet per tab, opened from that tab's own title.
 *
 * There is no settings *screen* and no gear. The title is the affordance, deliberately unmarked,
 * because the four preferences a clock has are ones you set once and giving them a permanent icon
 * in the app bar would cost more attention every day than it saves twice a year. Long-press would
 * have been more discoverable but is already spoken for on the world-clock rows.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsSheet(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.padding(top = 80.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SheetTitle("Alarm settings")

            NavigateRow(
                title = "Test alarm",
                subtitle = "Trigger a test alarm right now to test sound & ringing UI",
                onClick = { triggerTestAlarm(context) },
            )

            ChoiceRow(
                title = "Silence after",
                value = settings.alarms.silenceAfterMinutes,
                // 0 is "Never" (it rings until it is dismissed). Worth offering and worth putting
                // last rather than first: it is the answer for someone the default fails, not the
                // one to fall into by scrolling.
                options = listOf(1, 5, 10, 15, 20, 25, 30, 0),
                label = ::minutesLabel,
                onSelect = { v -> onChange { it.copy(alarms = it.alarms.copy(silenceAfterMinutes = v)) } },
            )
            ChoiceRow(
                title = "Snooze length",
                value = settings.alarms.snoozeMinutes,
                options = listOf(1, 5, 10, 15, 20, 30),
                label = ::minutesLabel,
                onSelect = { v -> onChange { it.copy(alarms = it.alarms.copy(snoozeMinutes = v)) } },
            )
            FullScreenIntentRow()
            ChoiceRow(
                title = "Start week on",
                value = settings.alarms.weekStart,
                options = WeekStart.entries,
                label = { it.label },
                onSelect = { v -> onChange { it.copy(alarms = it.alarms.copy(weekStart = v)) } },
            )

        }
    }
}

/**
 * Add a clock widget, and edit any already placed.
 *
 * This exists because `widgetFeatures="reconfigurable"` is explicitly only a *hint* to the host.
 * Several launchers have never offered a reconfigure affordance, and on those a placed widget would
 * otherwise be uneditable forever. Listing the live ids from `AppWidgetManager` means the app can
 * always get back to one.
 */
@Composable
private fun WidgetRows() {
    val context = LocalContext.current
    if (WidgetEntryPoints.canPin(context)) {
        NavigateRow(
            title = "Add a clock widget",
            subtitle = "Long-press it on the home screen to change its look",
            onClick = { WidgetEntryPoints.requestPin(context) },
        )
    } else {
        NavigateRow(
            title = "Clock widget",
            subtitle = "Add one from your home screen's widget picker",
            onClick = {},
        )
    }
}

/**
 * Offers the full-screen-intent grant, and only when it is missing.
 *
 * From Android 14 `USE_FULL_SCREEN_INTENT` is no longer install-granted to anything but calling
 * apps; for everyone else the declared permission is downgraded to a heads-up banner, which is
 * how a ringing alarm ends up as a notification you have to find rather than a screen you cannot
 * miss. `canUseFullScreenIntent()` is the documented way to ask, and
 * `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` the documented way to send the user to grant it.
 *
 * It is a row rather than a launch-time dialog on purpose: the app is perfectly usable without it,
 * and prompting for a special permission before the user has set a single alarm is how permission
 * prompts get dismissed reflexively.
 */
@Composable
private fun FullScreenIntentRow() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val context = LocalContext.current
    val nm = context.getSystemService(android.app.NotificationManager::class.java)
    // Re-read on every recomposition rather than remembering: the user grants this in Settings and
    // comes back, and a cached value would still say "not allowed" on their return.
    if (nm?.canUseFullScreenIntent() != false) return
    NavigateRow(
        title = "Full-screen alarms",
        subtitle = "Not allowed, so a ringing alarm will show as a banner instead",
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                )
            }
        },
    )
}

/**
 * The palette picker: a swatch per scheme, showing the three accents it actually produces.
 *
 * A single dot would be a lie here: the Expressive variant's whole point is that secondary and
 * tertiary rotate away from the seed, so two palettes with similar primaries can be completely
 * different to use. The swatch is a three-stop gradient of primary, tertiary and secondary for
 * that reason.
 */
@Composable
private fun PaletteRow(
    dynamic: Boolean,
    selected: Palette,
    onDynamic: () -> Unit,
    onSelect: (Palette) -> Unit,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val context = LocalContext.current
    val wallpaper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) {
            androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(context)
        }
    } else {
        null
    }

    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (wallpaper != null) {
            item(key = "wallpaper") {
                Swatch("Wallpaper", wallpaper, dynamic, onDynamic)
            }
        }
        items(Palette.entries, key = { it.name }) { palette ->
            Swatch(
                name = palette.displayName,
                scheme = if (dark) palette.dark else palette.light,
                selected = !dynamic && palette == selected,
                onClick = { onSelect(palette) },
            )
        }
    }
}

@Composable
private fun Swatch(
    name: String,
    scheme: androidx.compose.material3.ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        0f to scheme.primary,
                        0.5f to scheme.tertiary,
                        1f to scheme.secondary,
                    )
                )
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    shape = CircleShape,
                )
                .clickable(role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * Appearance and widgets, on the Stopwatch tab.
 *
 * They are app-wide rather than stopwatch-specific, and they used to hang off the alarm sheet
 * purely because that was the first sheet to exist. That made the longest settings screen in the
 * app the one for the feature with the most settings of its own. Stopwatch had none at all, so the
 * two shared concerns live here and each sheet is now about one thing.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StopwatchSettingsSheet(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.padding(top = 80.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SheetTitle("Appearance")
            ChoiceRow(
                title = "Theme",
                value = settings.theme.darkMode,
                options = DarkMode.entries,
                label = { it.label },
                onSelect = { v -> onChange { it.copy(theme = it.theme.copy(darkMode = v)) } },
            )
            SwitchRow(
                title = "Pure black",
                subtitle = "True black surfaces in dark mode, for OLED screens",
                checked = settings.theme.amoledBlack,
                onChange = { v -> onChange { it.copy(theme = it.theme.copy(amoledBlack = v)) } },
            )

            // Always on show, with the wallpaper as the first swatch rather than as a switch
            // somewhere above them. Hiding the palettes behind "wallpaper colours: off" meant that
            // on a fresh install, where wallpaper colour is the default, the theme picker looked
            // like it did not exist.
            PaletteRow(
                dynamic = settings.theme.dynamicColor,
                selected = settings.theme.palette,
                onDynamic = { onChange { it.copy(theme = it.theme.copy(dynamicColor = true)) } },
                onSelect = { v ->
                    onChange { it.copy(theme = it.theme.copy(palette = v, dynamicColor = false)) }
                },
            )

            SectionLabel("Widgets")
            WidgetRows()

            AboutRows()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WorldSettingsSheet(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.padding(top = 80.dp),
    ) {
        Column(Modifier.padding(bottom = 32.dp)) {
            SheetTitle("World clock settings")
            SwitchRow(
                title = "Display time with seconds",
                checked = settings.world.showSeconds,
                onChange = { v -> onChange { it.copy(world = it.world.copy(showSeconds = v)) } },
            )
            ChoiceRow(
                title = "Time format",
                value = settings.world.hourFormat,
                options = HourFormat.entries,
                label = { it.label },
                onSelect = { v -> onChange { it.copy(world = it.world.copy(hourFormat = v)) } },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TimerSettingsSheet(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            onChange { it.copy(timers = it.timers.copy(soundUri = picked?.toString() ?: SILENT)) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.padding(top = 80.dp),
    ) {
        Column(Modifier.padding(bottom = 32.dp)) {
            SheetTitle("Timer settings")
            NavigateRow(
                title = "Timer sound",
                subtitle = when (val u = settings.timers.soundUri) {
                    null -> "Default alarm sound"
                    SILENT -> "Silent"
                    else -> runCatching {
                        RingtoneManager.getRingtone(context, Uri.parse(u))?.getTitle(context)
                    }.getOrNull() ?: "Default alarm sound"
                },
                onClick = {
                    picker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Timer sound")
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    )
                },
            )
            SwitchRow(
                title = "Timer vibrate",
                checked = settings.timers.vibrate,
                onChange = { v -> onChange { it.copy(timers = it.timers.copy(vibrate = v)) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsSheet(
    settings: ClockSettings,
    onChange: ((ClockSettings) -> ClockSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.padding(top = 80.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SheetTitle("Settings & Customization")

            SectionLabel("Appearance & Theme")
            ChoiceRow(
                title = "Theme",
                value = settings.theme.darkMode,
                options = DarkMode.entries,
                label = { it.label },
                onSelect = { v -> onChange { it.copy(theme = it.theme.copy(darkMode = v)) } },
            )
            SwitchRow(
                title = "Pure black",
                subtitle = "True black surfaces in dark mode, for OLED screens",
                checked = settings.theme.amoledBlack,
                onChange = { v -> onChange { it.copy(theme = it.theme.copy(amoledBlack = v)) } },
            )

            PaletteRow(
                dynamic = settings.theme.dynamicColor,
                selected = settings.theme.palette,
                onDynamic = { onChange { it.copy(theme = it.theme.copy(dynamicColor = true)) } },
                onSelect = { v ->
                    onChange { it.copy(theme = it.theme.copy(palette = v, dynamicColor = false)) }
                },
            )

            SectionLabel("Alarms & Preferences")
            NavigateRow(
                title = "Test alarm",
                subtitle = "Trigger a test alarm right now to test sound & ringing UI",
                onClick = { triggerTestAlarm(context) },
            )
            ChoiceRow(
                title = "Start week on",
                value = settings.alarms.weekStart,
                options = WeekStart.entries,
                label = { it.label },
                onSelect = { v -> onChange { it.copy(alarms = it.alarms.copy(weekStart = v)) } },
            )
            ChoiceRow(
                title = "Silence after",
                value = settings.alarms.silenceAfterMinutes,
                options = listOf(1, 5, 10, 15, 20, 25, 30, 0),
                label = ::minutesLabel,
                onSelect = { v -> onChange { it.copy(alarms = it.alarms.copy(silenceAfterMinutes = v)) } },
            )
            ChoiceRow(
                title = "Snooze length",
                value = settings.alarms.snoozeMinutes,
                options = listOf(1, 5, 10, 15, 20, 30),
                label = ::minutesLabel,
                onSelect = { v -> onChange { it.copy(alarms = it.alarms.copy(snoozeMinutes = v)) } },
            )
            FullScreenIntentRow()

            SectionLabel("Widgets")
            WidgetRows()

            AboutRows()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 0 is never, 1 is singular. Both of these read wrong the moment they are not special-cased. */
private fun minutesLabel(minutes: Int): String = when (minutes) {
    0 -> "Never"
    1 -> "1 minute"
    else -> "$minutes minutes"
}
