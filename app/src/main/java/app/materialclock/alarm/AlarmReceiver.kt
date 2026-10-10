package app.materialclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.materialclock.data.ClockStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The alarm going off, and everything that can invalidate a schedule.
 *
 * ## Why one receiver for four unrelated broadcasts
 *
 * Boot, "user changed the clock", "user changed time zone" and "we just updated the app" all mean
 * the same thing to this app. Every `AlarmManager` entry it owns is now either gone or wrong, so
 * re-derive them from the stored alarms. `AlarmManager` keeps nothing across a reboot, and an
 * alarm set for 07:00 in Montréal must still be 07:00 after landing in Paris, which means the
 * absolute instant has to be recomputed rather than preserved.
 *
 * `MY_PACKAGE_REPLACED` is the one people forget: installing a new build tears down the old
 * process's alarms, and without it every debug install silently disarms the user's clock.
 *
 * ## goAsync
 *
 * Reading DataStore suspends and `onReceive` runs on the main thread with a hard deadline. The
 * pending result keeps the process alive across the coroutine; `finish()` must be reached on every
 * path or the receiver leaks and the system eventually kills the app for it.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(app, intent.getLongExtra(EXTRA_ID, -1L))
                    ACTION_TIMER_EXPIRED -> timerExpired(app)
                    else -> reschedule(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, id: Long) {
        val store = ClockStore(context)
        val alarm = store.alarmsNow().firstOrNull { it.id == id } ?: return

        // Clearing the snooze first matters: if the ring is what a snooze produced, leaving the
        // stamp in place would make `nextFire` keep returning a moment in the past forever.
        val cleared = store.alarmsNow().map { a ->
            when {
                a.id != id -> a
                // A one-shot has now happened. Disarm it rather than leave a dead row armed. That
                // holds *including* when this ring came from a snooze, the case an earlier version
                // missed: the snooze is that alarm's last occurrence, not an extra one, so
                // a one-shot snoozed at 06:45 and then dismissed at 06:55 must end up off. Pressing
                // Snooze again re-arms it, which is what ClockActionReceiver does deliberately.
                a.isOneShot -> a.copy(enabled = false, snoozedUntilMillis = null)
                else -> a.copy(snoozedUntilMillis = null)
            }
        }
        store.putAlarms(cleared)
        cleared.firstOrNull { it.id == id }?.let { AlarmScheduler.schedule(context, it) }

        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java)
                .setAction(AlarmService.ACTION_START)
                .putExtra(EXTRA_ID, alarm.id),
        )

        runCatching {
            val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
                putExtra(EXTRA_ID, alarm.id)
                putExtra(AlarmRingActivity.EXTRA_LABEL, alarm.label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(ringIntent)
        }
    }

    private suspend fun timerExpired(context: Context) {
        val store = ClockStore(context)
        store.timerNow() ?: return
        // Clear the timer, don't leave it at zero. A finished countdown is not a paused one, and a
        // stored timer at 00:00 makes the app open on a dead ring instead of the keypad.
        store.putTimer(null)
        // Only the ringer's own notification. An earlier version also posted a separate "Time's up"
        // and the shade showed the same sentence twice, one dismissible and one not.
        Notifications.hideTimer(context)
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START_TIMER),
        )
    }

    private suspend fun reschedule(context: Context) {
        val store = ClockStore(context)
        Notifications.ensureChannels(context)
        AlarmScheduler.scheduleAll(context, store.alarmsNow())
        // A reboot resets elapsedRealtime, so any stored deadline is meaningless. Rebuild the
        // timer's notification from what survived and let the store's own clamp decide.
        store.timerNow()?.let { TimerScheduler.sync(context, it) }
    }

    companion object {
        const val ACTION_FIRE = "app.materialclock.FIRE"
        const val ACTION_TIMER_EXPIRED = "app.materialclock.TIMER_EXPIRED"
        const val EXTRA_ID = "id"
    }
}
