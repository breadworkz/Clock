package app.materialclock.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One alarm.
 *
 * [days] empty means a one-shot: it fires at the next occurrence of [time] and disarms itself.
 * That is the same rule Android's own clock uses and it is why [nextFire] needs no separate flag.
 */
data class Alarm(
    val id: Long,
    val time: LocalTime,
    val label: String = "",
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    /** A `content://` ringtone URI, or null for the system default alarm sound. */
    val soundUri: String? = null,
    /**
     * Wall-clock millis a snooze is due, or null.
     *
     * Snooze lives on the alarm rather than in a side table because it has to survive a reboot and
     * because it *replaces* the next occurrence: an alarm snoozed at 06:45 must not also ring at
     * its usual 07:15 twenty seconds later. [nextFire] therefore returns the snooze when one is
     * pending, which keeps every caller (scheduler, list, boot receiver) automatically correct.
     */
    val snoozedUntilMillis: Long? = null,
) {
    val isOneShot: Boolean get() = days.isEmpty()

    /** The next instant this alarm would sound, or null when it is off. */
    fun nextFire(now: ZonedDateTime): ZonedDateTime? {
        if (!enabled) return null
        snoozedUntilMillis?.let { at ->
            val snooze = java.time.Instant.ofEpochMilli(at).atZone(now.zone)
            if (snooze.isAfter(now)) return snooze
        }
        val todayAt = now.with(time).withSecond(0).withNano(0)
        if (isOneShot) return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
        // Seven candidates is enough: a repeating alarm always has one inside a week.
        return (0..7).asSequence()
            .map { todayAt.plusDays(it.toLong()) }
            .firstOrNull { it.isAfter(now) && it.dayOfWeek in days }
    }

    /** The words the app prints: "Every day" / "Weekdays" / "Mon, Wed, Fri" / "Tomorrow". */
    fun repeatLabel(now: LocalDate = LocalDate.now()): String = when {
        isOneShot -> "Once"
        days.size == 7 -> "Every day"
        days == WEEKDAYS -> "Weekdays"
        days == WEEKENDS -> "Weekends"
        else -> DayOfWeek.entries.filter { it in days }.joinToString(", ") {
            it.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
        }
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKENDS: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/** "in 17 h 54 min", deliberately coarse because a countdown to tomorrow does not need seconds. */
fun humanUntil(from: ZonedDateTime, to: ZonedDateTime): String {
    val d = Duration.between(from, to)
    val days = d.toDays()
    val hours = d.toHours() % 24
    val mins = d.toMinutes() % 60
    return when {
        days > 0 -> "${days} d ${hours} h"
        hours > 0 -> "${hours} h ${mins} min"
        mins > 0 -> "${mins} min"
        else -> "less than a minute"
    }
}

/** A city on the world clock. [zone] is a real IANA id, so DST is the platform's problem. */
data class WorldCity(
    val zone: ZoneId,
    val city: String,
    val region: String,
) {
    fun timeAt(nowUtcMillis: Long): ZonedDateTime =
        java.time.Instant.ofEpochMilli(nowUtcMillis).atZone(zone)

    /** Whole hours from [home], signed. Half-hour zones round toward zero; the label prints exact. */
    fun offsetHours(home: ZoneId, nowUtcMillis: Long): Double {
        val inst = java.time.Instant.ofEpochMilli(nowUtcMillis)
        val a = zone.rules.getOffset(inst).totalSeconds
        val b = home.rules.getOffset(inst).totalSeconds
        return (a - b) / 3600.0
    }

    fun offsetLabel(home: ZoneId, nowUtcMillis: Long): String {
        val h = offsetHours(home, nowUtcMillis)
        val sign = if (h >= 0) "+" else "−"
        val abs = kotlin.math.abs(h)
        val whole = abs.toInt()
        val half = abs - whole
        val frac = when {
            half > 0.7 -> "¾"
            half > 0.4 -> "½"
            half > 0.2 -> "¼"
            else -> ""
        }
        return "$sign$whole$frac h"
    }

    /** Whether it is currently night there. Used to invert the row, as the concept does. */
    fun isNight(nowUtcMillis: Long): Boolean =
        timeAt(nowUtcMillis).hour.let { it < 6 || it >= 20 }

    /** 0..1 through the local day, which is what the concept's split discs encode. */
    fun dayFraction(nowUtcMillis: Long): Float {
        val t = timeAt(nowUtcMillis).toLocalTime()
        return (t.toSecondOfDay() / 86400f)
    }
}

enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * A countdown.
 *
 * Stored as a *deadline* rather than a remaining count, so it stays correct while the process is
 * dead and needs no tick to make progress. [remaining] is derived. Paused timers keep their
 * remaining time and have no deadline.
 */
data class ClockTimer(
    val id: Long,
    val label: String,
    val total: Duration,
    val state: TimerState = TimerState.IDLE,
    val deadlineElapsedMillis: Long = 0L,
    val pausedRemaining: Duration = total,
) {
    fun remaining(nowElapsedMillis: Long): Duration = when (state) {
        TimerState.RUNNING ->
            Duration.ofMillis((deadlineElapsedMillis - nowElapsedMillis).coerceAtLeast(0L))
        TimerState.IDLE -> total
        else -> pausedRemaining
    }

    fun fractionLeft(nowElapsedMillis: Long): Float {
        val t = total.toMillis().coerceAtLeast(1L)
        return (remaining(nowElapsedMillis).toMillis().toFloat() / t).coerceIn(0f, 1f)
    }
}

/** One completed lap: its own split, and the total elapsed at the moment it was taken. */
data class Lap(val index: Int, val split: Duration, val total: Duration)

/**
 * The stopwatch.
 *
 * Also deadline-style: [startedAtElapsed] plus [accumulated] survives a process death, where a
 * ticking counter would not. `elapsedRealtime` and not wall time, so changing the clock or
 * crossing a DST boundary mid-run cannot move it.
 */
data class Stopwatch(
    val running: Boolean = false,
    val startedAtElapsed: Long = 0L,
    val accumulated: Duration = Duration.ZERO,
    val laps: List<Lap> = emptyList(),
) {
    fun elapsed(nowElapsedMillis: Long): Duration =
        if (running) accumulated.plusMillis(nowElapsedMillis - startedAtElapsed) else accumulated

    val fastest: Int? get() = laps.minByOrNull { it.split }?.index?.takeIf { laps.size > 1 }
    val slowest: Int? get() = laps.maxByOrNull { it.split }?.index?.takeIf { laps.size > 1 }
}

/** `PT1H2M3S` → `1:02:03`, and under an hour → `12:34`. What every screen prints. */
fun Duration.clockFormat(withHours: Boolean = false): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0 || withHours) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** The stopwatch prints hundredths; anything slower would look frozen. */
fun Duration.stopwatchParts(): Triple<String, String, String> {
    val ms = toMillis().coerceAtLeast(0)
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    val s = (ms % 60_000) / 1000
    val cs = (ms % 1000) / 10
    return if (h > 0) {
        Triple("%02d".format(h), "%02d".format(m), "%02d".format(s))
    } else {
        Triple("%02d".format(m), "%02d".format(s), "%02d".format(cs))
    }
}

/** Splits a wall time into the concept's three parts: hour, minute, and the meridiem. */
fun LocalDateTime.parts(use24h: Boolean): Triple<String, String, String?> {
    val h = if (use24h) hour else ((hour % 12).takeIf { it != 0 } ?: 12)
    val ap = if (use24h) null else if (hour < 12) "AM" else "PM"
    return Triple("%02d".format(h), "%02d".format(minute), ap)
}
