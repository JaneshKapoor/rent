package com.rent.app.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Scrapes https://github.com/users/{username}/contributions (no auth required).
 *
 * As of the last inspection (2026) each day is:
 *   <td class="ContributionCalendar-day" data-date="2026-06-29"
 *       id="contribution-day-component-0-0" data-level="1" ...>
 * and the exact count lives in a SEPARATE element:
 *   <tool-tip for="contribution-day-component-0-0">7 contributions on June 29th.</tool-tip>
 *   (or "No contributions on ..." for zero).
 *
 * GitHub has changed this markup before (it used to be <rect data-count=...>),
 * so this parser is intentionally defensive:
 *   1. Prefer an inline count attribute if one is ever present (data-count).
 *   2. Otherwise join the <tool-tip for=...> element by id and parse its text.
 *   3. Otherwise estimate the count from data-level (0..4).
 */
class GitHubScraper(private val client: OkHttpClient) {

    fun fetch(username: String): List<ContributionDay> {
        val url = "https://github.com/users/$username/contributions"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("Contributions request failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw java.io.IOException("Empty contributions response")
            return parse(body)
        }
    }

    internal fun parse(html: String): List<ContributionDay> {
        val doc = Jsoup.parse(html)

        // Build id -> count from tool-tip elements first (current markup).
        val tooltipCounts = buildTooltipCountMap(doc)

        // Select day cells with fallbacks across markup revisions.
        var cells = doc.select("td.ContributionCalendar-day[data-date]")
        if (cells.isEmpty()) cells = doc.select("td[data-date]")
        if (cells.isEmpty()) cells = doc.select("rect[data-date]")

        if (cells.isEmpty()) {
            Log.w(TAG, "No day cells found — GitHub markup may have changed.")
            return emptyList()
        }

        val days = cells.mapNotNull { cell ->
            val date = cell.attr("data-date").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val level = firstIntAttr(cell, "data-level", "data-index") ?: 0
            val count = resolveCount(cell, tooltipCounts, level)
            ContributionDay(date = date, count = count, level = level)
        }

        // Oldest first, de-duplicated by date (keep the highest count seen).
        return days
            .groupBy { it.date }
            .map { (_, sameDate) -> sameDate.maxBy { it.count } }
            .sortedBy { it.date }
    }

    private fun resolveCount(
        cell: Element,
        tooltipCounts: Map<String, Int>,
        level: Int
    ): Int {
        // 1. Inline count attribute (older markup / possible future markup).
        firstIntAttr(cell, "data-count", "data-contribution-count")?.let { return it }

        // 2. Tooltip joined by id.
        val id = cell.attr("id")
        if (id.isNotBlank()) tooltipCounts[id]?.let { return it }

        // 3. Some markup embeds the count in the aria-label / title of the cell.
        parseCountFromText(cell.attr("aria-label"))?.let { return it }
        parseCountFromText(cell.attr("title"))?.let { return it }

        // 4. Last resort: estimate from the 0..4 level bucket.
        return LEVEL_ESTIMATE.getOrElse(level.coerceIn(0, 4)) { 0 }
    }

    private fun buildTooltipCountMap(doc: Document): Map<String, Int> {
        val tooltips = doc.select("tool-tip[for]")
        if (tooltips.isEmpty()) return emptyMap()
        val map = HashMap<String, Int>(tooltips.size)
        for (tip in tooltips) {
            val forId = tip.attr("for").takeIf { it.isNotBlank() } ?: continue
            val count = parseCountFromText(tip.text()) ?: continue
            map[forId] = count
        }
        return map
    }

    /**
     * Parses a leading contribution count from tooltip/label text such as:
     *   "7 contributions on June 29th."  -> 7
     *   "1 contribution on July 1st."    -> 1
     *   "No contributions on July 2nd."  -> 0
     */
    private fun parseCountFromText(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        if (trimmed.startsWith("No ", ignoreCase = true)) return 0
        val match = LEADING_NUMBER.find(trimmed) ?: return null
        return match.value.replace(",", "").toIntOrNull()
    }

    private fun firstIntAttr(el: Element, vararg names: String): Int? {
        for (name in names) {
            val v = el.attr(name)
            if (v.isNotBlank()) v.toIntOrNull()?.let { return it }
        }
        return null
    }

    companion object {
        private const val TAG = "GitHubScraper"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) RentWidget/1.0"
        private val LEADING_NUMBER = Regex("""^([\d,]+)""")

        // Rough count estimates per GitHub intensity level, used only when a
        // real count can't be found. Keeps streak logic sane if markup changes.
        private val LEVEL_ESTIMATE = intArrayOf(0, 1, 4, 8, 12)
    }
}
