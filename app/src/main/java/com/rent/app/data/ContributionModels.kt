package com.rent.app.data

import kotlinx.serialization.Serializable

/**
 * A single day in the contribution calendar.
 *
 * [date] is stored as an ISO-8601 string ("2026-07-04") so the whole
 * [ContributionState] can be serialized straight into DataStore without a
 * custom LocalDate serializer. Use [localDate] to get the parsed value.
 */
@Serializable
data class ContributionDay(
    val date: String,
    val count: Int,
    /** GitHub's 0..4 intensity bucket, kept as a defensive fallback for the count. */
    val level: Int = 0
) {
    val localDate: java.time.LocalDate
        get() = java.time.LocalDate.parse(date)
}

/**
 * The fully computed state the widget renders. Cached in DataStore so the
 * widget can draw instantly on boot before the next refresh finishes.
 */
@Serializable
data class ContributionState(
    /** Last ~84 days (oldest first) actually used for the heatmap. */
    val days: List<ContributionDay>,
    val streak: Int,
    val rentPaidToday: Boolean,
    val todayCount: Int,
    val threshold: Int,
    /** Epoch millis of the last successful refresh. */
    val lastUpdatedEpochMs: Long,
    /** True once a username has been configured and a fetch has succeeded at least once. */
    val configured: Boolean = true
) {
    companion object {
        /** Placeholder shown before setup / when no username is configured. */
        val NotConfigured = ContributionState(
            days = emptyList(),
            streak = 0,
            rentPaidToday = false,
            todayCount = 0,
            threshold = 10,
            lastUpdatedEpochMs = 0L,
            configured = false
        )
    }
}
