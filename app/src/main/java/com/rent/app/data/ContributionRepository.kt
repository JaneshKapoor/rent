package com.rent.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Owns the fetch-priority logic:
 *   - If a PAT is set -> GraphQL (more reliable).
 *   - Otherwise       -> scrape the public contributions page.
 * Computes the [ContributionState] and persists it to DataStore.
 */
class ContributionRepository private constructor(
    private val store: RentDataStore,
    private val scraper: GitHubScraper,
    private val graphQl: GitHubGraphQlApi
) {

    /**
     * Fetches fresh data, computes state, saves it, and returns it.
     * On failure returns the last cached state (or the NotConfigured placeholder)
     * so callers/the widget never crash.
     */
    suspend fun refresh(): ContributionState = withContext(Dispatchers.IO) {
        val settings = store.getSettings()

        if (settings.username.isBlank()) {
            val placeholder = ContributionState.NotConfigured
            return@withContext placeholder
        }

        try {
            val rawDays = if (settings.token.isNotBlank()) {
                Log.d(TAG, "Fetching via GraphQL (token present)")
                graphQl.fetch(settings.username, settings.token)
            } else {
                Log.d(TAG, "Fetching via public scrape")
                scraper.fetch(settings.username)
            }

            if (rawDays.isEmpty()) {
                throw java.io.IOException("No contribution days parsed")
            }

            val state = StreakCalculator.compute(rawDays, settings.threshold)
            store.saveState(state)
            state
        } catch (t: Throwable) {
            Log.w(TAG, "Refresh failed, falling back to cached state", t)
            store.getCachedState() ?: ContributionState.NotConfigured
        }
    }

    suspend fun cachedOrPlaceholder(): ContributionState =
        store.getCachedState() ?: ContributionState.NotConfigured

    companion object {
        private const val TAG = "ContributionRepo"

        @Volatile
        private var instance: ContributionRepository? = null

        fun get(context: Context): ContributionRepository {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(appContext: Context): ContributionRepository {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val json = Json { ignoreUnknownKeys = true }
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            return ContributionRepository(
                store = RentDataStore(appContext),
                scraper = GitHubScraper(client),
                graphQl = GitHubGraphQlApi(retrofit, client)
            )
        }
    }
}
