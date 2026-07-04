# Rent — GitHub Contribution Streak Widget

A native Android app whose only job is a **home-screen widget** that keeps you
accountable to your GitHub habit. It shows:

- **Top:** your current streak (big number) + a status line — **"Rent Paid :)"**
  when today's contributions meet your daily threshold, **"Rent Due :("** when
  they don't.
- **Bottom:** a GitHub-style contribution **heatmap** (up to a full year, drawn
  as square cells that span the widget width).

Everything is themeable from an in-app settings screen (palette, dark mode,
opacity, margins, weeks shown, threshold, auto-update).

> Prebuilt debug APK: [`dist/rent-debug.apk`](dist/rent-debug.apk)

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| UI (settings) | Jetpack **Compose** (Material 3) |
| Widget | Jetpack **Glance** 1.1.1 (`glance-appwidget`) |
| Background work | **WorkManager** 2.10 (`PeriodicWorkRequest` + `OneTimeWorkRequest`) |
| Persistence | **Preferences DataStore** 1.1.1 |
| Networking | **Retrofit** 2.11 + **OkHttp** 4.12 |
| JSON | **kotlinx.serialization** 1.7.3 |
| HTML scraping | **Jsoup** 1.18.1 |
| Build | Gradle (Kotlin DSL) + version catalog (`gradle/libs.versions.toml`) |
| SDK | `minSdk 26`, `targetSdk 34`, `compileSdk 35` |

`compileSdk 35` is required by `androidx.core`/`work`; `targetSdk` stays at 34.

---

## Project layout

```
app/src/main/java/com/rent/app/
├── RentApp.kt                 # Application: applies the Auto Update schedule on start
├── MainActivity.kt            # Compose settings UI (two tabs: SETTINGS / ABOUT APP)
├── data/
│   ├── ContributionModels.kt  # ContributionDay / ContributionState (serializable)
│   ├── RentDataStore.kt        # Preferences DataStore: settings + cached state
│   ├── GitHubScraper.kt        # PRIMARY fetch: scrape the public contributions page
│   ├── GitHubGraphQlApi.kt     # FALLBACK fetch: GitHub GraphQL v4 (needs a token)
│   ├── StreakCalculator.kt     # Turns raw days -> ContributionState (streak logic)
│   └── ContributionRepository.kt # Fetch-priority + persistence orchestration
├── widget/
│   ├── RentWidget.kt           # GlanceAppWidget: reads state, renders the widget
│   ├── RentWidgetReceiver.kt   # GlanceAppWidgetReceiver (registered in manifest)
│   ├── RefreshAction.kt        # Glance action: tapping the widget = refresh
│   ├── HeatmapRenderer.kt      # Draws the heatmap grid onto a Bitmap (Canvas)
│   ├── HeatmapPalette.kt        # GREEN / VIOLET / AMBER color presets
│   └── Palette.kt              # Card/text/accent colors + opacity handling
└── work/
    ├── RefreshWorker.kt        # CoroutineWorker: fetch -> compute -> save -> update widget
    └── RefreshScheduler.kt     # Enqueues periodic / one-time work
```

---

## How the data works

### 1. Fetching (with fallback)

`ContributionRepository.refresh()` picks a source by whether a Personal Access
Token is set:

1. **No token → scrape** `https://github.com/users/{username}/contributions`.
   This returns an HTML fragment. `GitHubScraper` parses it with Jsoup. GitHub's
   current markup is:

   ```html
   <td class="ContributionCalendar-day" data-date="2026-06-29"
       id="contribution-day-component-0-0" data-level="1"> ... </td>
   ...
   <tool-tip for="contribution-day-component-0-0">7 contributions on June 29th.</tool-tip>
   ```

   The exact count lives in a **separate `<tool-tip>`** joined to the cell by
   `id`. The parser is deliberately defensive — it tries, in order: an inline
   `data-count`, the joined tooltip text, the cell's `aria-label`/`title`, then a
   `data-level` (0–4) estimate; and it also falls back to the older
   `rect[data-date]` markup. If GitHub changes the markup again, it logs and
   returns the cached state instead of crashing.

2. **Token present → GraphQL** at `https://api.github.com/graphql`
   (`Authorization: Bearer <token>`), querying
   `user.contributionsCollection.contributionCalendar.weeks.contributionDays`.
   More reliable, so it's preferred whenever a token exists.

Retrofit + OkHttp handle transport; kotlinx.serialization decodes the GraphQL
JSON. The scrape returns a full year (~371 days).

### 2. Streak logic (`StreakCalculator`)

- A day "counts" if `contributionCount >= threshold` (default 10).
- **Today is treated as pending:** if today hasn't hit the threshold yet, the
  streak is **not** broken — the walk starts from *yesterday*, so your last
  confirmed streak stays visible until the day is fully over and unpaid.
- If today already meets the threshold, the walk includes today (rent paid).
- Streak = consecutive counting days walking backward, stopping at the first day
  below threshold.
- The result is a `ContributionState` (days + streak + rentPaidToday +
  todayCount + threshold + lastUpdated), which is serialized to JSON and cached
  in DataStore so the widget can render instantly.

### 3. Rendering the widget (`RentWidget` + `HeatmapRenderer`)

The heatmap is **drawn once onto a `Bitmap`** with Android `Canvas` and shown in
a single Glance `Image`. This is the important design decision: a ~53×7 grid of
individual Glance `Box` views would produce 370+ **RemoteViews**, which exceeds
the launcher's RemoteViews limit and renders only a partial grid. One bitmap =
one view = the whole grid, every time. Cells are square (image height follows the
grid's true aspect ratio) and span the full card width. The top text block is
sized down and top-aligned so the bottom row never clips.

The widget reads all appearance settings at render time: palette, dark-mode
background, background opacity (alpha), top/bottom margin, and weeks-to-show.

---

## When & how it updates

There are **four** update paths, so the widget stays fresh without draining
battery:

1. **Periodic background refresh (WorkManager).** When **Auto Update** is on
   (default), a `PeriodicWorkRequest` runs roughly **twice a day** (~12h
   interval), constrained to `NetworkType.CONNECTED`. It fetches → recomputes →
   saves to DataStore → calls `GlanceAppWidgetManager` to redraw every widget
   instance. Turning Auto Update off cancels this worker (manual only).

2. **Tap to refresh.** Tapping the widget fires a Glance `ActionCallback`
   (`RefreshAction`) that enqueues an immediate `OneTimeWorkRequest` — a real
   background fetch + in-place redraw, not just opening the app.

3. **Auto-refetch on render (self-heal).** Each time the widget draws, if a
   username is configured but the cache is missing or **older than 6 hours**, it
   kicks a background refresh. Staleness-based (not data-size based) so accounts
   with short history don't loop.

4. **Save & Refresh (in-app).** The settings button saves to DataStore and
   redraws the widget **instantly** for appearance changes (weeks, color, dark
   mode, opacity, margin — no network). It only performs a live GitHub fetch when
   a **data-related** field changes (username, token, or threshold), so tuning
   the look is immediate.

The widget also renders from the DataStore cache on boot/first placement, so it's
never blank while a fetch is in flight. If a fetch fails, it falls back to the
last cached `ContributionState`.

---

## Settings (persisted in DataStore)

| Setting | Effect |
|---|---|
| GitHub username | Which account to track |
| Personal Access Token (optional) | Switches fetching to the GraphQL API |
| Daily threshold (default 10) | Contributions needed to "pay rent" for the day |
| Contributions color | Heatmap + accent palette: **GitHub green / Violet / Amber** |
| Weeks to show (default **36**, 4–53) | How many week-columns the heatmap shows |
| Dark mode | Pure-black card background vs themed dark gray |
| Background opacity (0–100%) | Card alpha, to blend with the wallpaper |
| Top & bottom margin (0–32) | Inner vertical padding of the card |
| Auto update | Enables/cancels the twice-daily periodic worker |
| Turn off battery optimization | Launches the system exemption dialog (guarded by `PowerManager.isIgnoringBatteryOptimizations`) so the worker survives Doze |
| Add widget to home screen | `requestPinAppWidget` one-tap pinning (API 26+) |

---

## Build

Requirements: **JDK 17** and the **Android SDK** (platform 34 + 35, build-tools).
Easiest via Android Studio (bundles both). CLI:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"   # macOS
./gradlew assembleDebug
```

Output APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install onto a connected device/emulator:

```bash
./gradlew installDebug            # or: adb install -r <apk path>
```

## Using it

1. Launch **Rent**, enter your GitHub username (optionally a PAT), set the daily
   threshold, pick a palette, and tap **Save & Refresh Widget**.
2. Long-press the home screen → **Widgets** → **Rent** (or use the in-app
   *Add widget to home screen* button). Resize as you like.
3. Tapping the widget triggers an immediate background refresh; it also updates
   itself twice a day while Auto Update is on.
