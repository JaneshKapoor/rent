package com.rent.app.widget

import androidx.compose.ui.graphics.Color

/**
 * Heatmap color presets. Each has 4 intensity steps (dim -> brightest) plus a
 * shared "empty" cell color. Used by both the settings picker (Compose) and the
 * Glance widget — Glance shares [androidx.compose.ui.graphics.Color], so one
 * enum serves both.
 */
enum class HeatmapPalette(
    val displayName: String,
    /** Accent used for the main streak text so it matches the chosen palette. */
    val accent: Color,
    private val steps: List<Color>
) {
    GREEN(
        displayName = "GitHub green",
        accent = Color(0xFF3FB950),
        steps = listOf(
            Color(0xFF0E4429),
            Color(0xFF006D32),
            Color(0xFF26A641),
            Color(0xFF39D353)
        )
    ),
    PURPLE(
        displayName = "Violet",
        accent = Color(0xFFB392F0),
        steps = listOf(
            Color(0xFF3A1D6E),
            Color(0xFF6E40C9),
            Color(0xFF8957E5),
            Color(0xFFB392F0)
        )
    ),
    AMBER(
        displayName = "Amber",
        accent = Color(0xFFFFC93C),
        steps = listOf(
            Color(0xFF5C3B00),
            Color(0xFFB86E00),
            Color(0xFFE3941C),
            Color(0xFFFFC93C)
        )
    );

    /** Color for a cell with zero contributions. */
    val empty: Color = Color(0xFF21262D)

    /** 5 swatches (empty -> brightest) for the "Less → More" picker. */
    val swatches: List<Color> get() = listOf(empty) + steps

    /** Maps a raw contribution count to a heat color using the 5-step scale. */
    fun heatColor(count: Int): Color = when {
        count <= 0 -> empty
        count <= 2 -> steps[0]
        count <= 5 -> steps[1]
        count <= 9 -> steps[2]
        else -> steps[3]
    }

    companion object {
        fun fromNameOrDefault(name: String?): HeatmapPalette =
            entries.firstOrNull { it.name == name } ?: GREEN
    }
}
