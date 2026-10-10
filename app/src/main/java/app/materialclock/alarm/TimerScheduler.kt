package app.materialclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.getSystemService
import app.materialclock.core.ClockTimer
import app.materialclock.core.TimerState

/**
 * Keeps the timer's expiry alarm and its ongoing notification in step with the stored timer.
 *
 * There is one entry point, [sync], and that is deliberate. Start, pause, resume, +1 min and
 * cancel all reduce to "the timer is now *this*, make the world match", and giving each of them
 * its own method is how you end up with a paused timer that still has a live alarm behind it.
 *
 * The alarm is `ELAPSED_REALTIME_WAKEUP` because the timer is measured in elapsed time: a user who
 * changes the clock, or crosses a DST boundary, has not shortened their pasta timer.
 */
object TimerScheduler {

    fun sync(context: Context, timer: ClockTimer?) {
        val am = context.getSystemService<AlarmManager>() ?: return
        val pi = expiryIntent(context)
        am.cancel(pi)

        if (timer == null) {
            Notifications.hideTimer(context)
            return
        }
        if (timer.state == TimerState.RUNNING) {
            val at = timer.deadlineElapsedMillis
            if (at > SystemClock.elapsedRealtime()) {
                // `setAlarmClock`, not `setExactAndAllowWhileIdle`.
                //
                // The latter is the obvious choice and it is wrong: it is **quota-limited to one
                // firing per app per ~9 minutes**, and that was caught on device. A 15-second
                // timer started about a minute after the previous one expired never went off at
                // all, silently. Two short timers in a row is not an edge case for a kitchen
                // timer, it is the normal way one gets used.
                //
                // `setAlarmClock` carries no quota. The cost is the status-bar alarm glyph, which
                // is a fair description of what is pending anyway, and the trade is not close: a
                // timer that is occasionally silent is not a timer.
                //
                // It wants a wall-clock instant, while the deadline is deliberately elapsed-time,
                // so that changing the clock cannot shorten a countdown. Converting here keeps that
                // property: the *stored* timer stays immune, and only the scheduling handoff is in
                // wall time, over a window too short for a clock change to matter.
                val inMillis = at - SystemClock.elapsedRealtime()
                val show = Notifications.openApp(context, Notifications.TAB_TIMERS)
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(System.currentTimeMillis() + inMillis, show),
                    pi,
                )
            }
        }
        Notifications.showTimer(context, timer)
    }

    private fun expiryIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        900,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_TIMER_EXPIRED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
