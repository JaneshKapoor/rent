# Rent — GitHub Contribution Streak Widget

A native Android app (Kotlin, Jetpack Compose + Glance) whose only job is a
home-screen widget showing:

- **Top:** current streak + `Rent Paid ✅` / `Rent Due ⚠️` based on whether today's
  GitHub contributions hit your daily threshold.
- **Bottom:** a GitHub-style contribution heatmap (7 rows × up to 12 week columns).

## Requirements

- **JDK 17** (Android Gradle Plugin 8.7 requires it)
- **Android SDK** with platform 34 (`compileSdk`/`targetSdk = 34`, `minSdk = 26`)
- Easiest path: install **Android Studio** (bundles a JDK + SDK + emulator).

This machine currently has no JDK/SDK, so the build must be run after installing
the above (or opened in Android Studio, which provides both).

## One-time setup

1. Install Android Studio → open this folder (`rent/`). Studio will download SDK 34
   if needed and create `local.properties` pointing at your SDK.

   If building purely from the CLI instead, create `local.properties` in the project
   root with your SDK path, e.g.:

   ```
   sdk.dir=/Users/<you>/Library/Android/sdk
   ```

   and point `JAVA_HOME` at a JDK 17, e.g.:

   ```
   export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
   ```

## Build a debug APK

```bash
./gradlew assembleDebug
```

Output APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install onto a connected device/emulator:

```bash
./gradlew installDebug
# or
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Using it

1. Launch **Rent**, enter your GitHub username. Optionally add a Personal Access
   Token (public scrape is used with no token; GraphQL is used when a token is set).
   Set the daily threshold (default 10). Tap **Save & Refresh Widget**.
2. Long-press the home screen → **Widgets** → **Rent** → drop it on the screen.
   Resize between 4×2 and 4×3.
3. Tapping the widget triggers an immediate background refresh in place.
   A `PeriodicWorkRequest` also refreshes hourly (network-constrained).

## Architecture

| Layer | Files |
|-------|-------|
| Settings UI | `MainActivity.kt` (Compose) |
| Storage | `data/RentDataStore.kt` (Preferences DataStore; caches `ContributionState` as JSON) |
| Fetch (primary, no auth) | `data/GitHubScraper.kt` (Jsoup; parses `td.ContributionCalendar-day` + joined `<tool-tip>` counts, with fallbacks for older `rect[data-count]` markup and `data-level` estimates) |
| Fetch (fallback, PAT) | `data/GitHubGraphQlApi.kt` (Retrofit + kotlinx.serialization) |
| Priority + persistence | `data/ContributionRepository.kt` |
| Streak logic | `data/StreakCalculator.kt` |
| Background refresh | `work/RefreshWorker.kt`, `work/RefreshScheduler.kt` (WorkManager) |
| Widget | `widget/RentWidget.kt`, `RentWidgetReceiver.kt`, `RefreshAction.kt`, `Palette.kt` (Glance 1.1.1) |

### GitHub markup note

The scraper was written against the **current** markup of
`https://github.com/users/{username}/contributions`, where each day is a
`<td class="ContributionCalendar-day" data-date data-level id>` and the exact
count lives in a separate `<tool-tip for="{id}">7 contributions on June 29th.</tool-tip>`
element. If GitHub changes this again, the parser degrades gracefully (falls back
to `data-level` estimates, then to the cached state) rather than crashing.
