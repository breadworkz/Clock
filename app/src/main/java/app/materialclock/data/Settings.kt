package app.materialclock.data

import app.materialclock.ui.theme.Palette
import java.time.DayOfWeek

/**
 * Every preference the app has, in one tree.
 *
 * Grouped by the tab that owns them, because that is how they are reached: there is no settings
 * screen, only a settings sheet per tab, opened from that tab's own title. A flat bag would have
 * made it impossible to tell which sheet a new preference belongs on.
 */
data class ClockSettings(
    val alarms: AlarmSettings = AlarmSettings(),
    val world: WorldClockSettings = WorldClockSettings(),
    val timers: TimerSettings = TimerSettings(),
    val theme: ThemeSettings = ThemeSettings(),
)

data class AlarmSettings(
    /** How long a ringing alarm keeps ringing before it gives up. */
    /** Minutes a ringing alarm keeps going before it gives up. **0 means never.** */
    val silenceAfterMinutes: Int = 10,
    val snoozeMinutes: Int = 10,
    val weekStart: WeekStart = WeekStart.SYSTEM,
)

data class WorldClockSettings(
    val showSeconds: Boolean = false,
    val hourFormat: HourFormat = HourFormat.SYSTEM,
)

data class TimerSettings(
    /** A `content://` ringtone URI, or null for the system default alarm sound. */
    val soundUri: String? = null,
    val vibrate: Boolean = true,
)

data class ThemeSettings(
    /**
     * Wallpaper colour is the default, per the platform's own guidance: a clock is a background
     * app and should look like it belongs to the phone rather than to itself. [palette] is what it
     * falls back to when dynamic colour is off, or unavailable below API 31.
     */
    val dynamicColor: Boolean = true,
    val palette: Palette = Palette.CONCEPT,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val amoledBlack: Boolean = false,
)

enum class DarkMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class HourFormat(val label: String) { SYSTEM("System default"), H12("12-hour"), H24("24-hour") }

enum class WeekStart(val label: String, val day: DayOfWeek?) {
    SYSTEM("System default", null),
    SATURDAY("Saturday", DayOfWeek.SATURDAY),
    SUNDAY("Sunday", DayOfWeek.SUNDAY),
    MONDAY("Monday", DayOfWeek.MONDAY),
}

/**
 * The seven weekday letters, rotated to start on [start].
 *
 * The alarm tile draws these, so the setting has to reach all the way into the grid rather than
 * only into a picker. An app that starts the week on Monday in the editor and on Sunday in the
 * tile is worse than one that never offered the choice.
 */
fun WeekStart.order(systemFirstDay: DayOfWeek): List<DayOfWeek> {
    val first = day ?: systemFirstDay
    return (0..6).map { DayOfWeek.of((first.value - 1 + it) % 7 + 1) }
}
