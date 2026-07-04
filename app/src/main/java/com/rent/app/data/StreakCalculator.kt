package com.rent.app.data

import java.time.Instant
import java.time.LocalDate

/**
 * Turns a raw list of contribution days into a [ContributionState].
 *
 * Streak rule (per spec):
 *  - A day "counts" if its contributionCount >= threshold.
 *  - Today is treated as *pending*: if today hasn't hit the threshold yet we
 *    do NOT break the streak — we start walking backward from yesterday, so the
 *    last confirmed streak stays visible until the day is fully over and unpaid.
 *  - If today already meets the threshold, the walk starts at today (rent paid).
 *  - Streak = consecutive counting days walking backward, stopping at the first
 *    day that doesn't meet the threshold.
 */
object StreakCalculator {

    fun compute(
        rawDays: List<ContributionDay>,
        threshold: Int,
        today: LocalDate = LocalDate.now(),
        now: Instant = Instant.now(),
        // Keep up to a full year so the widget can show a user-chosen number of
        // weeks; the widget itself trims to the last N columns at render time.
        heatmapDays: Int = 371
    ): ContributionState {
        val safeThreshold = threshold.coerceAtLeast(1)
        val byDate: Map<LocalDate, Int> = rawDays.associate { it.localDate to it.count }

        val todayCount = byDate[today] ?: 0
        val rentPaidToday = todayCount >= safeThreshold

        // Start at today if paid, otherwise at yesterday (today is still pending).
        val startDay = if (rentPaidToday) today else today.minusDays(1)

        var streak = 0
        var cursor = startDay
        while (true) {
            val count = byDate[cursor] ?: break
            if (count >= safeThreshold) {
                streak++
                cursor = cursor.minusDays(1)
            } else {
                break
            }
        }

        // Keep only the most recent [heatmapDays] for the widget grid.
        val trimmed = rawDays
            .sortedBy { it.date }
            .takeLast(heatmapDays)

        return ContributionState(
            days = trimmed,
            streak = streak,
            rentPaidToday = rentPaidToday,
            todayCount = todayCount,
            threshold = safeThreshold,
            lastUpdatedEpochMs = now.toEpochMilli(),
            configured = true
        )
    }
}
