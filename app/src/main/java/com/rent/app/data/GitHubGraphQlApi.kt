package com.rent.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * FALLBACK path: GitHub GraphQL API v4. Used whenever a PAT is provided, since
 * it's more reliable than scraping. Returns exact daily contribution counts.
 */
interface GitHubGraphQlService {
    @POST("graphql")
    suspend fun query(
        @Header("Authorization") authorization: String,
        @Body body: GraphQlRequest
    ): GraphQlResponse
}

@Serializable
data class GraphQlRequest(
    val query: String,
    val variables: Variables
) {
    @Serializable
    data class Variables(val login: String)
}

@Serializable
data class GraphQlResponse(
    val data: Data? = null,
    val errors: List<GraphQlError>? = null
) {
    @Serializable
    data class Data(val user: User? = null)

    @Serializable
    data class User(
        val contributionsCollection: ContributionsCollection? = null
    )

    @Serializable
    data class ContributionsCollection(
        val contributionCalendar: ContributionCalendar? = null
    )

    @Serializable
    data class ContributionCalendar(
        val weeks: List<Week> = emptyList()
    )

    @Serializable
    data class Week(
        val contributionDays: List<Day> = emptyList()
    )

    @Serializable
    data class Day(
        val date: String,
        @SerialName("contributionCount") val contributionCount: Int
    )
}

@Serializable
data class GraphQlError(val message: String)

/**
 * Wraps the Retrofit service with the query string and response flattening.
 */
class GitHubGraphQlApi(
    retrofit: Retrofit,
    private val client: OkHttpClient
) {
    private val service = retrofit.create(GitHubGraphQlService::class.java)

    suspend fun fetch(username: String, token: String): List<ContributionDay> {
        val request = GraphQlRequest(
            query = QUERY,
            variables = GraphQlRequest.Variables(login = username)
        )
        val response = service.query(authorization = "Bearer $token", body = request)

        response.errors?.firstOrNull()?.let {
            throw java.io.IOException("GraphQL error: ${it.message}")
        }

        val weeks = response.data?.user?.contributionsCollection
            ?.contributionCalendar?.weeks
            ?: throw java.io.IOException("GraphQL returned no contribution calendar")

        return weeks
            .flatMap { it.contributionDays }
            .map { ContributionDay(date = it.date, count = it.contributionCount) }
            .sortedBy { it.date }
    }

    companion object {
        private val QUERY = """
            query(${'$'}login: String!) {
              user(login: ${'$'}login) {
                contributionsCollection {
                  contributionCalendar {
                    weeks {
                      contributionDays {
                        date
                        contributionCount
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
