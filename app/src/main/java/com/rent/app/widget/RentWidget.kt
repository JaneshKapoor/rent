package com.rent.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rent.app.MainActivity
import com.rent.app.data.ContributionState
import com.rent.app.data.RentDataStore
import com.rent.app.work.RefreshScheduler

/**
 * The home-screen widget. Two stacked sections:
 *   TOP: streak number + "Rent Paid :)" / "Rent Due :(" status.
 *   BOTTOM: GitHub-style contribution heatmap, drawn as a single bitmap so it
 *   reliably fills the full width regardless of week count (see [HeatmapRenderer]).
 */
class RentWidget : GlanceAppWidget() {

    // Resize-friendly: content adapts to the box the launcher gives us.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = RentDataStore(context)
        val state = store.getCachedState() ?: ContributionState.NotConfigured
        val settings = store.getSettings()

        // Auto-refetch: if a username is configured but the cache is missing or
        // stale, kick a background refresh which redraws the widget when done.
        val ageMs = System.currentTimeMillis() - state.lastUpdatedEpochMs
        val stale = state.lastUpdatedEpochMs == 0L || ageMs > STALE_AFTER_MS
        if (settings.username.isNotBlank() && (!state.configured || stale)) {
            RefreshScheduler.refreshNow(context)
        }

        val appearance = WidgetAppearance(
            palette = settings.palette,
            darkMode = settings.darkMode,
            opacity = settings.backgroundOpacity,
            marginDp = settings.marginDp,
            weeksToShow = settings.weeksToShow
        )
        val heatmap = HeatmapRenderer.render(state.days, appearance.palette, appearance.weeksToShow)

        provideContent {
            WidgetContent(state, appearance, heatmap)
        }
    }

    companion object {
        /** Redraws every placed instance of this widget. */
        suspend fun updateAll(context: Context) {
            RentWidget().updateAll(context)
        }
    }
}

private const val CARD_H_PADDING = 8
private const val CARD_V_PADDING = 6
private const val STALE_AFTER_MS = 6 * 60 * 60 * 1000L // auto-refetch when older than 6h

/** Appearance settings the widget reads at render time. */
data class WidgetAppearance(
    val palette: HeatmapPalette,
    val darkMode: Boolean,
    val opacity: Int,
    val marginDp: Int,
    val weeksToShow: Int
)

@Composable
private fun WidgetContent(
    state: ContributionState,
    appearance: WidgetAppearance,
    heatmap: Bitmap
) {
    val card = GlanceModifier
        .fillMaxSize()
        .background(Palette.cardBackground(appearance.darkMode, appearance.opacity))
        .cornerRadius(20.dp)
        .padding(
            start = CARD_H_PADDING.dp,
            end = CARD_H_PADDING.dp,
            top = (CARD_V_PADDING + appearance.marginDp).dp,
            bottom = (CARD_V_PADDING + 8 + appearance.marginDp).dp
        )

    if (!state.configured || state.days.isEmpty() && state.lastUpdatedEpochMs == 0L) {
        PlaceholderContent(card.clickable(actionStartActivity<MainActivity>()))
    } else {
        Column(
            modifier = card.clickable(actionRunCallback<RefreshAction>()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusSection(state, appearance.palette)
            Spacer(GlanceModifier.height(8.dp))
            // Graph takes the remaining vertical space. ContentScale.Fit keeps
            // cells square and scales to fit whichever of width/height is the
            // binding constraint, so it never clips for smaller week counts.
            Image(
                provider = ImageProvider(heatmap),
                contentDescription = "GitHub contribution heatmap",
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

@Composable
private fun StatusSection(state: ContributionState, palette: HeatmapPalette) {
    val paid = state.rentPaidToday
    // Main text AND status follow the chosen palette (green / violet / amber).
    val accent = palette.accent
    val statusText = if (paid) "Rent Paid :)" else "Rent Due :("
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
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
        // Number is already shown above, so this is just a label.
        Text(
            text = "day streak",
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = statusText,
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 14.sp,
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
