package app.materialclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.materialclock.core.ClockTimer
import app.materialclock.core.Lap
import app.materialclock.core.Stopwatch
import app.materialclock.core.TimerState
import app.materialclock.data.ClockStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * The buttons on the notifications, and nothing else.
 *
 * Every one of these does exactly what the equivalent button in the app does, by writing the same
 * store, which is the reason the app needs no code to hear about it. `ClockStore` exposes flows,
 * the view model collects them, so pausing a timer from the shade updates the running screen
 * behind it with no broadcast, no binder and no event bus.
 */
class ClockActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            val store = ClockStore(app)
            try {
                when (intent.action) {
                    ACTION_SNOOZE -> snooze(app, store, id)
                    ACTION_DISMISS -> AlarmService.stop(app)
                    ACTION_TIMER_TOGGLE -> timerToggle(app, store)
                    ACTION_TIMER_ADD -> timerAdd(app, store)
                    ACTION_TIMER_CANCEL -> {
                        AlarmService.stop(app)
                        store.putTimer(null)
                        TimerScheduler.sync(app, null)
                    }
                    ACTION_SW_TOGGLE -> stopwatchToggle(app, store)
                    ACTION_SW_LAP -> stopwatchLap(app, store)
                    ACTION_SW_RESET -> {
                        store.putStopwatch(Stopwatch())
                        Notifications.hideStopwatch(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun snooze(context: Context, store: ClockStore, id: Long) {
        AlarmService.stop(context)
        val minutes = store.settingsNow().alarms.snoozeMinutes
        val due = System.currentTimeMillis() + minutes * 60_000L
        val updated = store.alarmsNow().map {
            // Re-arming matters for a one-shot: firing disabled it, and a snooze has to bring it
            // back or the second ring never happens.
            if (it.id == id) it.copy(snoozedUntilMillis = due, enabled = true) else it
        }
        store.putAlarms(updated)
        updated.firstOrNull { it.id == id }?.let { AlarmScheduler.schedule(context, it) }
    }

    private suspend fun timerToggle(context: Context, store: ClockStore) {
        val t = store.timerNow() ?: return
        val now = SystemClock.elapsedRealtime()
        val next = when (t.state) {
            TimerState.RUNNING -> t.copy(state = TimerState.PAUSED, pausedRemaining = t.remaining(now))
            else -> t.copy(state = TimerState.RUNNING, deadlineElapsedMillis = now + t.pausedRemaining.toMillis())
        }
        store.putTimer(next)
        TimerScheduler.sync(context, next)
    }

    private suspend fun timerAdd(context: Context, store: ClockStore) {
        val t = store.timerNow() ?: return
        val next: ClockTimer = when (t.state) {
            TimerState.RUNNING -> t.copy(
                total = t.total.plusMinutes(1),
                deadlineElapsedMillis = t.deadlineElapsedMillis + 60_000L,
            )
            else -> t.copy(total = t.total.plusMinutes(1), pausedRemaining = t.pausedRemaining.plusMinutes(1))
        }
        store.putTimer(next)
        TimerScheduler.sync(context, next)
    }

    private suspend fun stopwatchToggle(context: Context, store: ClockStore) {
        val sw = store.stopwatch.first()
        val now = SystemClock.elapsedRealtime()
        val next = if (sw.running) {
            sw.copy(running = false, accumulated = sw.elapsed(now))
        } else {
            sw.copy(running = true, startedAtElapsed = now)
        }
        store.putStopwatch(next)
        Notifications.showStopwatch(context, next)
    }

    private suspend fun stopwatchLap(context: Context, store: ClockStore) {
        val sw = store.stopwatch.first()
        if (!sw.running) return
        val total = sw.elapsed(SystemClock.elapsedRealtime())
        val previous = sw.laps.firstOrNull()?.total ?: Duration.ZERO
        val next = sw.copy(laps = listOf(Lap(sw.laps.size + 1, total.minus(previous), total)) + sw.laps)
        store.putStopwatch(next)
        Notifications.showStopwatch(context, next)
    }

    companion object {
        const val ACTION_SNOOZE = "app.materialclock.SNOOZE"
        const val ACTION_DISMISS = "app.materialclock.DISMISS"
        const val ACTION_TIMER_TOGGLE = "app.materialclock.TIMER_TOGGLE"
        const val ACTION_TIMER_ADD = "app.materialclock.TIMER_ADD"
        const val ACTION_TIMER_CANCEL = "app.materialclock.TIMER_CANCEL"
        const val ACTION_SW_TOGGLE = "app.materialclock.SW_TOGGLE"
        const val ACTION_SW_LAP = "app.materialclock.SW_LAP"
        const val ACTION_SW_RESET = "app.materialclock.SW_RESET"
    }
}
