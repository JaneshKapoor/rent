package com.rent.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rent.app.MainActivity
import com.rent.app.data.ContributionDay
import com.rent.app.data.ContributionState
import com.rent.app.data.RentDataStore

/**
 * The home-screen widget. Two stacked sections:
 *   TOP: streak number + "Rent Paid ✅" / "Rent Due ⚠️" status.
 *   BOTTOM: GitHub-style contribution heatmap (7 rows x up to 12 week columns).
 *
 * State is read from the DataStore cache so the widget renders instantly; a tap
 * kicks off an immediate background refresh (see [RefreshAction]).
 */
class RentWidget : GlanceAppWidget() {

    // Resize-friendly: content adapts to the box the launcher gives us.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = RentDataStore(context)
        val state = store.getCachedState() ?: ContributionState.NotConfigured
        val settings = store.getSettings()
        val appearance = WidgetAppearance(
            palette = settings.palette,
            darkMode = settings.darkMode,
            opacity = settings.backgroundOpacity,
            marginDp = settings.marginDp
        )
        provideContent {
            WidgetContent(state, appearance)
        }
    }

    companion object {
        /** Redraws every placed instance of this widget. */
        suspend fun updateAll(context: Context) {
            RentWidget().updateAll(context)
        }
    }
}

private const val CELL_DP = 12
private const val CELL_GAP_DP = 3
private const val MAX_WEEKS = 12

/** Appearance settings the widget reads at render time. */
data class WidgetAppearance(
    val palette: HeatmapPalette,
    val darkMode: Boolean,
    val opacity: Int,
    val marginDp: Int
)

@Composable
private fun WidgetContent(state: ContributionState, appearance: WidgetAppearance) {
    val outerModifier = GlanceModifier
        .fillMaxSize()
        .background(Palette.cardBackground(appearance.darkMode, appearance.opacity))
        .cornerRadius(20.dp)
        .padding(horizontal = 14.dp, vertical = (10 + appearance.marginDp).dp)

    if (!state.configured || state.days.isEmpty() && state.lastUpdatedEpochMs == 0L) {
        PlaceholderContent(outerModifier)
        return
    }

    Column(
        modifier = outerModifier.clickable(actionRunCallback<RefreshAction>())
    ) {
        StatusSection(state)
        Spacer(GlanceModifier.height(12.dp))
        Heatmap(state.days, appearance.palette)
    }
}

@Composable
private fun StatusSection(state: ContributionState) {
    val paid = state.rentPaidToday
    val accent = if (paid) Palette.PaidAccent else Palette.DueAccent
    val statusText = if (paid) "Rent Paid ✅" else "Rent Due ⚠️"
    val subtitle = if (paid) {
        "${state.todayCount} today · threshold ${state.threshold}"
    } else {
        "${state.todayCount}/${state.threshold} today · streak at risk"
    }
    val cardTone = if (paid) Palette.PaidCard else Palette.DueCard

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(cardTone)
            .cornerRadius(14.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Big streak number.
        Text(
            text = state.streak.toString(),
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = statusText,
                style = TextStyle(
                    color = ColorProvider(Palette.TextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = if (state.streak == 1) "1 day streak" else "${state.streak} day streak",
                style = TextStyle(
                    color = ColorProvider(accent),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    color = ColorProvider(Palette.TextSecondary),
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun Heatmap(days: List<ContributionDay>, palette: HeatmapPalette) {
    val size = LocalSize.current
    // How many week-columns fit the current widget width.
    val available = size.width.value.toInt() - 28 // account for outer padding
    val perColumn = CELL_DP + CELL_GAP_DP
    val fitColumns = (available / perColumn).coerceIn(1, MAX_WEEKS)

    val weeks = buildWeeks(days).takeLast(fitColumns)

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        weeks.forEachIndexed { index, week ->
            Column {
                week.forEach { day ->
                    Box(
                        modifier = GlanceModifier
                            .size(CELL_DP.dp)
                            .background(palette.heatColor(day?.count ?: 0))
                            .cornerRadius(2.dp)
                    ) {}
                    Spacer(GlanceModifier.height(CELL_GAP_DP.dp))
                }
            }
            if (index != weeks.lastIndex) {
                Spacer(GlanceModifier.width(CELL_GAP_DP.dp))
            }
        }
    }
}

@Composable
private fun PlaceholderContent(modifier: GlanceModifier) {
    Box(
        modifier = modifier.clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Rent",
                style = TextStyle(
                    color = ColorProvider(Palette.TextPrimary),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "Tap to set up Rent",
                style = TextStyle(
                    color = ColorProvider(Palette.TextSecondary),
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * Arranges a flat oldest-first day list into GitHub-style week columns of 7
 * rows each (row 0 = Sunday). The first column is front-padded with nulls so
 * weekday alignment is preserved.
 */
private fun buildWeeks(days: List<ContributionDay>): List<List<ContributionDay?>> {
    if (days.isEmpty()) return emptyList()
    val padded = ArrayList<ContributionDay?>()
    // dayOfWeek: MONDAY=1..SUNDAY=7 ; map to Sunday=0..Saturday=6.
    val leadingPad = days.first().localDate.dayOfWeek.value % 7
    repeat(leadingPad) { padded.add(null) }
    padded.addAll(days)
    return padded.chunked(7)
}
