package com.rent.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
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
            marginDp = settings.marginDp,
            weeksToShow = settings.weeksToShow
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

/** Appearance settings the widget reads at render time. */
data class WidgetAppearance(
    val palette: HeatmapPalette,
    val darkMode: Boolean,
    val opacity: Int,
    val marginDp: Int,
    val weeksToShow: Int
)

@Composable
private fun WidgetContent(state: ContributionState, appearance: WidgetAppearance) {
    // The card wraps its content width so it only spans as wide as the text /
    // heatmap need, and is centered within whatever cell the launcher gives us.
    val card = GlanceModifier
        .wrapContentWidth()
        .background(Palette.cardBackground(appearance.darkMode, appearance.opacity))
        .cornerRadius(20.dp)
        .padding(horizontal = 16.dp, vertical = (12 + appearance.marginDp).dp)

    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (!state.configured || state.days.isEmpty() && state.lastUpdatedEpochMs == 0L) {
            PlaceholderContent(card.clickable(actionStartActivity<MainActivity>()))
        } else {
            Column(
                modifier = card.clickable(actionRunCallback<RefreshAction>()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusSection(state, appearance.palette)
                Spacer(GlanceModifier.height(12.dp))
                Heatmap(state.days, appearance.palette, appearance.weeksToShow)
            }
        }
    }
}

@Composable
private fun StatusSection(state: ContributionState, palette: HeatmapPalette) {
    val paid = state.rentPaidToday
    // Main text follows the chosen palette (green / violet / amber).
    val accent = palette.accent
    val statusText = if (paid) "Rent Paid ✅" else "Rent Due ⚠️"
    val statusColor = if (paid) Palette.PaidAccent else Palette.DueAccent
    val subtitle = if (paid) {
        "${state.todayCount} contributions today"
    } else {
        "${state.todayCount}/${state.threshold} contributions today"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Big streak number, palette-accented.
        Text(
            text = state.streak.toString(),
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
        Text(
            text = if (state.streak == 1) "1 day streak" else "${state.streak} day streak",
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = statusText,
            style = TextStyle(
                color = ColorProvider(statusColor),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
        Text(
            text = subtitle,
            style = TextStyle(
                color = ColorProvider(Palette.TextSecondary),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun Heatmap(days: List<ContributionDay>, palette: HeatmapPalette, weeksToShow: Int) {
    val columns = weeksToShow.coerceIn(RentDataStore.MIN_WEEKS, RentDataStore.MAX_WEEKS)
    val weeks = buildWeeks(days).takeLast(columns)

    // wrapContentWidth so the heatmap hugs its columns and stays centered.
    Row(modifier = GlanceModifier.wrapContentWidth()) {
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
        modifier = modifier,
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
