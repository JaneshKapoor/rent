package com.rent.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import com.rent.app.data.ContributionDay
import com.rent.app.data.RentDataStore

/**
 * Draws the contribution heatmap into a single [Bitmap] instead of hundreds of
 * Glance Box views. A grid of ~53x7 cells would produce 370+ RemoteViews, which
 * exceeds the launcher's RemoteViews limit and renders only partially. One
 * bitmap in a single Image view sidesteps that entirely and always fills width.
 */
object HeatmapRenderer {

    private const val CELL = 26      // px per cell in the source bitmap
    private const val GAP = 2        // px gap between cells (smaller -> bigger cells)
    private const val RADIUS = 4f    // rounded corner radius
    private const val ROWS = 7       // days per week (Sun..Sat)

    fun render(
        days: List<ContributionDay>,
        palette: HeatmapPalette,
        weeksToShow: Int
    ): Bitmap {
        val columns = weeksToShow.coerceIn(RentDataStore.MIN_WEEKS, RentDataStore.MAX_WEEKS)
        val weeks = buildWeeks(days, columns)

        val width = columns * CELL + (columns - 1) * GAP
        val height = ROWS * CELL + (ROWS - 1) * GAP
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        weeks.forEachIndexed { col, week ->
            for (row in 0 until ROWS) {
                val day = week.getOrNull(row)
                val left = (col * (CELL + GAP)).toFloat()
                val top = (row * (CELL + GAP)).toFloat()
                paint.color = palette.heatColor(day?.count ?: 0).toArgb()
                canvas.drawRoundRect(left, top, left + CELL, top + CELL, RADIUS, RADIUS, paint)
            }
        }
        return bmp
    }

    /**
     * Arranges the flat oldest-first day list into exactly [columns] week columns
     * of 7 rows (row 0 = Sunday). Leading days are front-padded so weekday
     * alignment holds, and the whole grid is left-padded with empty weeks so it
     * always returns [columns] columns (fills the full width even on short data).
     */
    private fun buildWeeks(
        days: List<ContributionDay>,
        columns: Int
    ): List<List<ContributionDay?>> {
        val padded = ArrayList<ContributionDay?>()
        if (days.isNotEmpty()) {
            // dayOfWeek: MONDAY=1..SUNDAY=7 ; map to Sunday=0..Saturday=6.
            val leadingPad = days.first().localDate.dayOfWeek.value % 7
            repeat(leadingPad) { padded.add(null) }
            padded.addAll(days)
        }
        val recent = padded.chunked(7).takeLast(columns)
        if (recent.size >= columns) return recent
        val emptyWeek = List<ContributionDay?>(ROWS) { null }
        return List(columns - recent.size) { emptyWeek } + recent
    }
}
