package app.materialclock.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.materialclock.alarm.AlarmScheduler
import app.materialclock.alarm.AlarmService
import app.materialclock.alarm.Notifications
import app.materialclock.alarm.TimerScheduler
import app.materialclock.core.Alarm
import app.materialclock.core.ClockTimer
import app.materialclock.core.Lap
import app.materialclock.core.Stopwatch
import app.materialclock.core.TimerState
import app.materialclock.core.WorldCity
import app.materialclock.data.ClockSettings
import app.materialclock.data.ClockStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId

/**
 * A frame-paced clock, for anything that has to look continuous.
 *
 * `withFrameMillis` rather than a fixed `delay`: the stopwatch shows hundredths, and a counter
 * sampled on its own schedule beats against the display's refresh and visibly stutters. Tying the
 * read to Compose's own frame clock gives one sample per drawn frame, which is both the minimum
 * needed and the maximum useful. It also stops on its own when the composition leaves the screen.
 */
@Composable
fun rememberElapsedTicker(active: Boolean = true): State<Long> =
    produceState(initialValue = SystemClock.elapsedRealtime(), active) {
        while (active) {
            withFrameMillis { }
            value = SystemClock.elapsedRealtime()
        }
    }

/**
 * A wall-clock ticker at [periodMillis].
 *
 * The world clock and the alarm list change once a second at most, and waking every frame to
 * re-derive six time zones would burn battery for nothing. It re-aligns to the second boundary
 * each tick so the displayed seconds turn over when they should rather than drifting.
 */
@Composable
fun rememberWallTicker(periodMillis: Long = 1000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis(), periodMillis) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            delay(periodMillis - (now % periodMillis))
        }
    }

/**
 * The whole app's state, read from and written to [ClockStore].
 *
 * Nothing is held in memory as the source of truth. Every mutation writes the store and the flows
 * come back around. That is what makes a notification button and an on-screen button the same
 * operation, and what makes the app correct after being killed. The cost is one extra hop per
 * press, which for a clock is free.
 *
 * Scheduling is a *consequence* of a write, not a parallel path: `AlarmScheduler` and
 * `TimerScheduler` are called with the value that was just persisted, so the store and
 * `AlarmManager` cannot disagree.
 */
class ClockViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ClockStore(app)
    private val ctx get() = getApplication<Application>()

    val settings: StateFlow<ClockSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ClockSettings())
    val alarms: StateFlow<List<Alarm>> =
        store.alarms.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cities: StateFlow<List<WorldCity>> =
        store.cities.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val timer: StateFlow<ClockTimer?> =
        store.timer.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val stopwatch: StateFlow<Stopwatch> =
        store.stopwatch.stateIn(viewModelScope, SharingStarted.Eagerly, Stopwatch())

    val homeZone: ZoneId = ZoneId.systemDefault()

    init {
        // The first run has never scheduled anything, and a reinstall wipes AlarmManager. Doing it
        // on launch is cheap and covers both without a separate first-run flag.
        viewModelScope.launch {
            Notifications.ensureChannels(ctx)
            AlarmScheduler.scheduleAll(ctx, store.alarmsNow())
        }
    }

    /* ── Settings ───────────────────────────────────────────────────────────────────────────── */

    /**
     * Returns Unit, not the Job, so it can be passed where `((ClockSettings) -> ClockSettings) ->
     * Unit` is expected. Nothing else has to be notified: silence-after, snooze length and the
     * timer sound are all read from the store at the moment they are needed, by whichever receiver
     * or service needs them.
     */
    fun updateSettings(block: (ClockSettings) -> ClockSettings) {
        viewModelScope.launch { store.update(block) }
    }

    /* ── Alarms ─────────────────────────────────────────────────────────────────────────────── */

    fun toggleAlarm(id: Long) = viewModelScope.launch {
        val next = store.alarmsNow().map {
            if (it.id == id) it.copy(enabled = !it.enabled, snoozedUntilMillis = null) else it
        }
        store.putAlarms(next)
        next.firstOrNull { it.id == id }?.let { a ->
            if (a.enabled) AlarmScheduler.schedule(ctx, a) else AlarmScheduler.cancel(ctx, id)
        }
    }

    /** Insert or replace. A null [Alarm.id] of 0 means "new", which is what the sheet sends. */
    fun saveAlarm(draft: Alarm) = viewModelScope.launch {
        val id = if (draft.id == 0L) store.nextId() else draft.id
        val alarm = draft.copy(id = id, snoozedUntilMillis = null)
        val current = store.alarmsNow()
        val next = if (current.any { it.id == id }) {
            current.map { if (it.id == id) alarm else it }
        } else {
            current + alarm
        }
        store.putAlarms(next.sortedWith(compareBy({ it.time.hour }, { it.time.minute })))
        AlarmScheduler.schedule(ctx, alarm)
    }

    fun deleteAlarm(id: Long) = viewModelScope.launch {
        AlarmScheduler.cancel(ctx, id)
        store.putAlarms(store.alarmsNow().filterNot { it.id == id })
    }

    /* ── World clock ────────────────────────────────────────────────────────────────────────── */

    fun addCity(city: WorldCity) = viewModelScope.launch {
        val current = store.cities.first()
        if (current.none { it.zone == city.zone }) store.putCities(current + city)
    }

    fun removeCity(zone: ZoneId) = viewModelScope.launch {
        store.putCities(store.cities.first().filterNot { it.zone == zone })
    }

    /* ── Timer ──────────────────────────────────────────────────────────────────────────────── */

    /**
     * Digits fill from the right the way every phone keypad works: "2", "5" reads as 25 seconds,
     * then a third digit pushes it into minutes. Kept in memory rather than the store, because a
     * half-typed duration is not state worth surviving a reboot.
     */
    var timerDigits: String by mutableStateOf("")
        private set

    val draftDuration: Duration
        get() {
            val d = timerDigits.padStart(6, '0')
            return Duration.ofHours(d.substring(0, 2).toLong())
                .plusMinutes(d.substring(2, 4).toLong())
                .plusSeconds(d.substring(4, 6).toLong())
        }

    fun pressDigit(c: Char) {
        if (timerDigits.length >= 6) return
        if (timerDigits.isEmpty() && c == '0') return
        timerDigits += c
    }

    fun backspace() {
        timerDigits = timerDigits.dropLast(1)
    }

    /** The winder. Sets whole minutes, which is what a spring-wound dial can actually express. */
    fun windToMinutes(minutes: Int) {
        timerDigits = if (minutes <= 0) "" else "%d%02d00".format(minutes / 60, minutes % 60).trimStart('0')
    }

    fun startTimer(label: String = "Timer") = viewModelScope.launch {
        val total = draftDuration
        if (total.isZero) return@launch
        val t = ClockTimer(
            id = store.nextId(),
            label = label,
            total = total,
            state = TimerState.RUNNING,
            deadlineElapsedMillis = SystemClock.elapsedRealtime() + total.toMillis(),
        )
        timerDigits = ""
        store.putTimer(t)
        TimerScheduler.sync(ctx, t)
    }

    fun pauseOrResumeTimer() = viewModelScope.launch {
        val t = store.timerNow() ?: return@launch
        val now = SystemClock.elapsedRealtime()
        val next = when (t.state) {
            TimerState.RUNNING -> t.copy(state = TimerState.PAUSED, pausedRemaining = t.remaining(now))
            else -> t.copy(state = TimerState.RUNNING, deadlineElapsedMillis = now + t.pausedRemaining.toMillis())
        }
        store.putTimer(next)
        TimerScheduler.sync(ctx, next)
    }

    /** +10 s, the one adjustment a running timer needs. Extends the deadline, not a counter. */
    fun addTenSeconds() = viewModelScope.launch {
        val t = store.timerNow() ?: return@launch
        val next = when (t.state) {
            TimerState.RUNNING -> t.copy(
                total = t.total.plusSeconds(10),
                deadlineElapsedMillis = t.deadlineElapsedMillis + 10_000,
            )
            else -> t.copy(total = t.total.plusSeconds(10), pausedRemaining = t.pausedRemaining.plusSeconds(10))
        }
        store.putTimer(next)
        TimerScheduler.sync(ctx, next)
    }

    fun cancelTimer() = viewModelScope.launch {
        AlarmService.stop(ctx)
        store.putTimer(null)
        TimerScheduler.sync(ctx, null)
    }

    /* ── Stopwatch ──────────────────────────────────────────────────────────────────────────── */

    fun toggleStopwatch() = viewModelScope.launch {
        val sw = store.stopwatch.first()
        val now = SystemClock.elapsedRealtime()
        val next = if (sw.running) {
            sw.copy(running = false, accumulated = sw.elapsed(now))
        } else {
            sw.copy(running = true, startedAtElapsed = now)
        }
        store.putStopwatch(next)
        Notifications.showStopwatch(ctx, next)
    }

    fun lap() = viewModelScope.launch {
        val sw = store.stopwatch.first()
        if (!sw.running) return@launch
        val total = sw.elapsed(SystemClock.elapsedRealtime())
        val previous = sw.laps.firstOrNull()?.total ?: Duration.ZERO
        // Newest first: the list is read from the top and the freshest lap is the one you took.
        val next = sw.copy(laps = listOf(Lap(sw.laps.size + 1, total.minus(previous), total)) + sw.laps)
        store.putStopwatch(next)
        Notifications.showStopwatch(ctx, next)
    }

    fun resetStopwatch() = viewModelScope.launch {
        store.putStopwatch(Stopwatch())
        Notifications.hideStopwatch(ctx)
    }

    /** Seeds a brand-new alarm for the editor: the next round hour, on no particular days. */
    fun blankAlarm(): Alarm = Alarm(
        id = 0L,
        time = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0),
        label = "",
        days = emptySet<DayOfWeek>(),
    )
}
