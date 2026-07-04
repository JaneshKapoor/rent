package com.rent.app.widget

import androidx.compose.ui.graphics.Color

/**
 * Dark-theme-friendly color palette for the widget. The heatmap uses a 5-step
 * green scale reminiscent of GitHub's, but on a dark card so it reads well over
 * varied launcher wallpapers.
 */
object Palette {
    val CardBorder = Color(0xFF30363D)

    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)

    val PaidAccent = Color(0xFF3FB950)        // green
    val DueAccent = Color(0xFFF0883E)         // warm orange

    // Warm gradient-ish paid background & cool due background (single tones,
    // since Glance's Box background is a flat color).
    val PaidCard = Color(0xFF14261A)
    val DueCard = Color(0xFF2A1C10)

    // Widget card backgrounds: pure black when "dark mode" is on, else a themed
    // dark gray. Opacity (0..100) lets the card blend with the wallpaper.
    private val DarkGrayCard = Color(0xFF161B22)

    fun cardBackground(darkMode: Boolean, opacityPercent: Int): Color {
        val base = if (darkMode) Color(0xFF000000) else DarkGrayCard
        val alpha = (opacityPercent.coerceIn(0, 100)) / 100f
        return base.copy(alpha = alpha)
    }
}
