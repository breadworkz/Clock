package app.materialclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.materialclock.core.Alarm
import app.materialclock.core.ClockTimer
import app.materialclock.core.Lap
import app.materialclock.core.Stopwatch
import app.materialclock.core.TimerState
import app.materialclock.core.WorldCity
import app.materialclock.ui.theme.Palette
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.prefs: DataStore<Preferences> by preferencesDataStore("clock")

/**
 * Everything that has to survive the process.
 *
 * Preferences DataStore rather than Room: the whole dataset is a handful of alarms, a handful of
 * cities and two dozen scalars, and none of it is queried; it is read once at start and written
 * whole. A database would add a schema, a migration story and a code generator to store what fits
 * in a few kilobytes of JSON.
 *
 * Alarms and cities are JSON *strings* inside the preference file, using `org.json`, which is in
 * the platform. Adding kotlinx-serialization for two record types would be a plugin, a dependency
 * and a compiler step to save about forty lines.
 *
 * Reads are cold flows. Writes are suspending and atomic per call, so a crash mid-write leaves the
 * previous state rather than half of the new one. For the alarm list that is the difference between
 * an alarm that does not ring and an alarm list that cannot be parsed.
 */
class ClockStore(private val context: Context) {

    val settings: Flow<ClockSettings> = context.prefs.data.map { it.toSettings() }
    val alarms: Flow<List<Alarm>> = context.prefs.data.map { p ->
        p[KEY_ALARMS]?.let(::parseAlarms) ?: SEED_ALARMS
    }
    val cities: Flow<List<WorldCity>> = context.prefs.data.map { p ->
        p[KEY_CITIES]?.let(::parseCities) ?: SEED_CITIES
    }
    val timer: Flow<ClockTimer?> = context.prefs.data.map { p -> p[KEY_TIMER]?.let(::parseTimer) }
    val stopwatch: Flow<Stopwatch> = context.prefs.data.map { p ->
        p[KEY_STOPWATCH]?.let(::parseStopwatch) ?: Stopwatch()
    }

    /** A one-shot read, for the receivers and services that have no scope to collect in. */
    suspend fun settingsNow(): ClockSettings = settings.first()
    suspend fun alarmsNow(): List<Alarm> = alarms.first()
    suspend fun timerNow(): ClockTimer? = timer.first()

    suspend fun putAlarms(list: List<Alarm>) {
        context.prefs.edit { it[KEY_ALARMS] = encodeAlarms(list) }
    }

    suspend fun putCities(list: List<WorldCity>) {
        context.prefs.edit { it[KEY_CITIES] = encodeCities(list) }
    }

    suspend fun putTimer(t: ClockTimer?) {
        context.prefs.edit { p -> if (t == null) p.remove(KEY_TIMER) else p[KEY_TIMER] = encodeTimer(t) }
    }

    suspend fun putStopwatch(s: Stopwatch) {
        context.prefs.edit { it[KEY_STOPWATCH] = encodeStopwatch(s) }
    }

    /** Monotonic ids, so a rescheduled alarm never collides with a PendingIntent request code. */
    suspend fun nextId(): Long {
        var out = 0L
        context.prefs.edit { p ->
            out = (p[KEY_NEXT_ID] ?: 100L)
            p[KEY_NEXT_ID] = out + 1
        }
        return out
    }

    /* ── Widgets ────────────────────────────────────────────────────────────────────────── */

    /**
     * One key per widget id, not one blob holding them all.
     *
     * `DataStore.edit` is atomic per call but not across calls, and two config activities can be
     * alive at once, whether that is two widgets dropped in quick succession or a reconfigure
     * racing a pin callback. A shared map would lose the first write. Per-key also makes deletion
     * a `remove` and the orphan sweep a prefix scan.
     */
    fun widgetConfig(id: Int): Flow<WidgetConfig?> =
        context.prefs.data.map { p -> p[widgetKey(id)]?.let(::parseWidgetConfig) }

    suspend fun widgetConfigNow(id: Int): WidgetConfig? = widgetConfig(id).first()

    suspend fun putWidgetConfig(id: Int, config: WidgetConfig) {
        context.prefs.edit { it[widgetKey(id)] = encodeWidgetConfig(config) }
    }

    /**
     * Also the cancelled-add path: a host that never gets `RESULT_OK` deletes the id and sends
     * `onDeleted`, so one cleanup site covers both and the two cases need not be told apart.
     */
    suspend fun deleteWidgetConfigs(ids: IntArray) {
        context.prefs.edit { p -> ids.forEach { p.remove(widgetKey(it)) } }
    }

    /**
     * Restore-from-backup remap. Every removal happens before any write, in one transaction,
     * because an id may appear in both lists and a naive pairwise loop would delete what it just
     * wrote.
     */
    suspend fun remapWidgetConfigs(old: IntArray, new: IntArray) {
        context.prefs.edit { p ->
            val carried = old.map { p[widgetKey(it)] }
            old.forEach { p.remove(widgetKey(it)) }
            carried.forEachIndexed { i, value ->
                if (value != null && i < new.size) p[widgetKey(new[i])] = value
            }
        }
    }

    /** Safety net for records whose widget vanished without an `onDeleted` (a restore, a crash). */
    suspend fun sweepWidgetConfigs(liveIds: IntArray) {
        val live = liveIds.toHashSet()
        context.prefs.edit { p ->
            p.asMap().keys
                .map { it.name }
                .filter { it.startsWith(WIDGET_PREFIX) }
                .filter { it.removePrefix(WIDGET_PREFIX).toIntOrNull() !in live }
                .forEach { p.remove(stringPreferencesKey(it)) }
        }
    }

    suspend fun update(block: (ClockSettings) -> ClockSettings) {
        context.prefs.edit { p -> p.writeSettings(block(p.toSettings())) }
    }

    private companion object {
        const val WIDGET_PREFIX = "widget:"
        fun widgetKey(id: Int) = stringPreferencesKey("$WIDGET_PREFIX$id")

        val KEY_ALARMS = stringPreferencesKey("alarms")
        val KEY_CITIES = stringPreferencesKey("cities")
        val KEY_TIMER = stringPreferencesKey("timer")
        val KEY_STOPWATCH = stringPreferencesKey("stopwatch")
        val KEY_NEXT_ID = longPreferencesKey("nextId")

        val KEY_SILENCE = intPreferencesKey("silenceAfter")
        val KEY_SNOOZE = intPreferencesKey("snooze")
        val KEY_WEEK_START = stringPreferencesKey("weekStart")
        val KEY_SECONDS = booleanPreferencesKey("showSeconds")
        val KEY_HOUR_FORMAT = stringPreferencesKey("hourFormat")
        val KEY_TIMER_SOUND = stringPreferencesKey("timerSound")
        val KEY_TIMER_VIBRATE = booleanPreferencesKey("timerVibrate")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamicColor")
        val KEY_PALETTE = stringPreferencesKey("palette")
        val KEY_DARK = stringPreferencesKey("darkMode")
        val KEY_AMOLED = booleanPreferencesKey("amoled")

        val SEED_ALARMS = listOf(
            Alarm(1, LocalTime.of(6, 40), "Gym", setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            Alarm(2, LocalTime.of(7, 15), "Work", Alarm.WEEKDAYS),
            Alarm(3, LocalTime.of(8, 0), "Standup", setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), enabled = false),
        )

        val SEED_CITIES = listOf(
            WorldCity(ZoneId.of("America/Los_Angeles"), "San Francisco", "United States"),
            WorldCity(ZoneId.of("Europe/London"), "London", "United Kingdom"),
            WorldCity(ZoneId.of("Asia/Tokyo"), "Tokyo", "Japan"),
        )
    }
}

/* ── Settings ⇄ preferences ─────────────────────────────────────────────────────────────────── */

private fun Preferences.toSettings() = ClockSettings(
    alarms = AlarmSettings(
        silenceAfterMinutes = this[intPreferencesKey("silenceAfter")] ?: 10,
        snoozeMinutes = this[intPreferencesKey("snooze")] ?: 10,
        weekStart = enumOr(this[stringPreferencesKey("weekStart")], WeekStart.SYSTEM),
    ),
    world = WorldClockSettings(
        showSeconds = this[booleanPreferencesKey("showSeconds")] ?: false,
        hourFormat = enumOr(this[stringPreferencesKey("hourFormat")], HourFormat.SYSTEM),
    ),
    timers = TimerSettings(
        soundUri = this[stringPreferencesKey("timerSound")],
        vibrate = this[booleanPreferencesKey("timerVibrate")] ?: true,
    ),
    theme = ThemeSettings(
        dynamicColor = this[booleanPreferencesKey("dynamicColor")] ?: true,
        palette = enumOr(this[stringPreferencesKey("palette")], Palette.CONCEPT),
        darkMode = enumOr(this[stringPreferencesKey("darkMode")], DarkMode.SYSTEM),
        amoledBlack = this[booleanPreferencesKey("amoled")] ?: false,
    ),
)

private fun androidx.datastore.preferences.core.MutablePreferences.writeSettings(s: ClockSettings) {
    this[intPreferencesKey("silenceAfter")] = s.alarms.silenceAfterMinutes
    this[intPreferencesKey("snooze")] = s.alarms.snoozeMinutes
    this[stringPreferencesKey("weekStart")] = s.alarms.weekStart.name
    this[booleanPreferencesKey("showSeconds")] = s.world.showSeconds
    this[stringPreferencesKey("hourFormat")] = s.world.hourFormat.name
    s.timers.soundUri
        ?.let { this[stringPreferencesKey("timerSound")] = it }
        ?: remove(stringPreferencesKey("timerSound"))
    this[booleanPreferencesKey("timerVibrate")] = s.timers.vibrate
    this[booleanPreferencesKey("dynamicColor")] = s.theme.dynamicColor
    this[stringPreferencesKey("palette")] = s.theme.palette.name
    this[stringPreferencesKey("darkMode")] = s.theme.darkMode.name
    this[booleanPreferencesKey("amoled")] = s.theme.amoledBlack
}

/** Falls back rather than throwing: a preference file written by a newer build must not crash. */
private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback

/* ── JSON ───────────────────────────────────────────────────────────────────────────────────── */

private fun encodeAlarms(list: List<Alarm>) = JSONArray().apply {
    list.forEach { a ->
        put(
            JSONObject()
                .put("id", a.id)
                .put("time", a.time.toSecondOfDay())
                .put("label", a.label)
                .put("days", JSONArray(a.days.map { it.value }))
                .put("enabled", a.enabled)
                .put("vibrate", a.vibrate)
                .apply { a.soundUri?.let { put("sound", it) } }
                .apply { a.snoozedUntilMillis?.let { put("snoozed", it) } }
        )
    }
}.toString()

private fun parseAlarms(s: String): List<Alarm> = runCatching {
    val arr = JSONArray(s)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val days = o.getJSONArray("days")
        Alarm(
            id = o.getLong("id"),
            time = LocalTime.ofSecondOfDay(o.getLong("time")),
            label = o.optString("label", ""),
            days = (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
            enabled = o.optBoolean("enabled", true),
            vibrate = o.optBoolean("vibrate", true),
            soundUri = o.optString("sound").takeIf { it.isNotEmpty() },
            snoozedUntilMillis = o.optLong("snoozed").takeIf { it > 0L },
        )
    }
}.getOrDefault(emptyList())

/* ── Widget config ⇄ JSON ───────────────────────────────────────────────────────────────── */

private fun encodeWidgetConfig(c: WidgetConfig) = JSONObject()
    .put("v", 1)
    .put("shape", c.shape.name)
    .put("pill", c.pillOrientation.name)
    .put("fit", c.fit.name)
    .put("fill", c.faceFill.name)
    .put("outline", c.outline.name)
    .put("hour", c.hourHand.name)
    .put("minute", c.minuteHand.name)
    .put("second", c.secondHand.name)
    .put("pin", c.centrePin)
    .put("numerals", c.numerals.name)
    .put("indices", c.indexSet.name)
    .put("minor", c.minorIndices.name)
    .put("numeralSize", c.numeralSize.name)
    .put("numeralLayout", c.numeralLayout.name)
    .put("numeralWidth", c.numeralWidth.name)
    .put("numeralWeight", c.numeralWeight.name)
    .put("numeralRound", c.numeralRound.name)
    .put("date", c.date.name)
    .put("datePos", c.datePosition.name)
    .put("colour", c.colour.name)
    .put("palette", c.palette.name)
    .toString()

/**
 * Never null and never throws.
 *
 * This is parsed inside a `BroadcastReceiver` with a ten-second deadline and no UI. A record from a
 * newer build, or a truncated write, must come back as a plain working circle, because the
 * alternative is a widget the user can neither see nor repair.
 */
private fun parseWidgetConfig(s: String): WidgetConfig = runCatching {
    val o = JSONObject(s)
    WidgetConfig(
        shape = enumOr(o.optString("shape"), FaceShape.CIRCLE),
        pillOrientation = enumOr(o.optString("pill"), PillOrientation.HORIZONTAL),
        fit = enumOr(o.optString("fit"), FitMode.STRETCH),
        faceFill = enumOr(o.optString("fill"), FaceFill.CONTAINER),
        outline = enumOr(o.optString("outline"), OutlineStyle.NONE),
        hourHand = enumOr(o.optString("hour"), HandStyle.BATON),
        minuteHand = enumOr(o.optString("minute"), HandStyle.BATON),
        secondHand = enumOr(o.optString("second"), HandStyle.OFF),
        centrePin = o.optBoolean("pin", true),
        numerals = enumOr(o.optString("numerals"), NumeralSystem.ARABIC),
        indexSet = enumOr(o.optString("indices"), IndexSet.QUARTERS),
        minorIndices = enumOr(o.optString("minor"), MinorIndex.DOTS),
        numeralSize = enumOr(o.optString("numeralSize"), NumeralSize.MEDIUM),
        numeralLayout = enumOr(o.optString("numeralLayout"), NumeralLayout.CIRCLE_UPRIGHT),
        numeralWidth = enumOr(o.optString("numeralWidth"), NumeralWidth.NORMAL),
        numeralWeight = enumOr(o.optString("numeralWeight"), NumeralWeight.MEDIUM),
        numeralRound = enumOr(o.optString("numeralRound"), NumeralRound.NONE),
        date = enumOr(o.optString("date"), DateMode.NONE),
        datePosition = enumOr(o.optString("datePos"), DatePosition.FOUR_THIRTY),
        colour = enumOr(o.optString("colour"), ColourSource.FOLLOW_APP),
        palette = enumOr(o.optString("palette"), Palette.CONCEPT),
    )
}.getOrDefault(WidgetConfig())

private fun encodeCities(list: List<WorldCity>) = JSONArray().apply {
    list.forEach {
        put(JSONObject().put("zone", it.zone.id).put("city", it.city).put("region", it.region))
    }
}.toString()

private fun parseCities(s: String): List<WorldCity> = runCatching {
    val arr = JSONArray(s)
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        // A zone id can vanish between tzdb releases; drop the row rather than the whole list.
        runCatching { WorldCity(ZoneId.of(o.getString("zone")), o.getString("city"), o.getString("region")) }
            .getOrNull()
    }
}.getOrDefault(emptyList())

private fun encodeTimer(t: ClockTimer) = JSONObject()
    .put("id", t.id)
    .put("label", t.label)
    .put("total", t.total.toMillis())
    .put("state", t.state.name)
    .put("deadline", t.deadlineElapsedMillis)
    .put("paused", t.pausedRemaining.toMillis())
    .toString()

private fun parseTimer(s: String): ClockTimer? = runCatching {
    val o = JSONObject(s)
    ClockTimer(
        id = o.getLong("id"),
        label = o.getString("label"),
        total = Duration.ofMillis(o.getLong("total")),
        state = enumOr(o.getString("state"), TimerState.IDLE),
        deadlineElapsedMillis = o.getLong("deadline"),
        pausedRemaining = Duration.ofMillis(o.getLong("paused")),
    )
}.getOrNull()

private fun encodeStopwatch(s: Stopwatch) = JSONObject()
    .put("running", s.running)
    .put("startedAt", s.startedAtElapsed)
    .put("accumulated", s.accumulated.toMillis())
    .put(
        "laps",
        JSONArray().apply {
            s.laps.forEach {
                put(
                    JSONObject().put("i", it.index)
                        .put("split", it.split.toMillis())
                        .put("total", it.total.toMillis())
                )
            }
        }
    )
    .toString()

private fun parseStopwatch(s: String): Stopwatch = runCatching {
    val o = JSONObject(s)
    val laps = o.getJSONArray("laps")
    Stopwatch(
        running = o.getBoolean("running"),
        startedAtElapsed = o.getLong("startedAt"),
        accumulated = Duration.ofMillis(o.getLong("accumulated")),
        laps = (0 until laps.length()).map {
            val l = laps.getJSONObject(it)
            Lap(l.getInt("i"), Duration.ofMillis(l.getLong("split")), Duration.ofMillis(l.getLong("total")))
        },
    )
}.getOrDefault(Stopwatch())
