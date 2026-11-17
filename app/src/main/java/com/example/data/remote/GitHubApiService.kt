package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubRepoDto(
    val id: Long = 0L,
    val name: String = "",
    @Json(name = "full_name") val fullName: String = "",
    val description: String? = null,
    @Json(name = "private") val isPrivateRepo: Boolean = false,
    val language: String? = null,
    @Json(name = "stargazers_count") val stars: Int = 0,
    @Json(name = "open_issues_count") val openIssues: Int = 0,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "html_url") val htmlUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubIssueDto(
    val id: Long = 0L,
    val number: Int = 0,
    val title: String = "",
    val state: String = "open",
    val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubFileContentDto(
    val name: String = "",
    val path: String = "",
    val type: String = "file",
    val size: Long = 0L,
    val content: String? = null,
    val encoding: String? = null,
    @Json(name = "html_url") val htmlUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubGistFile(
    val content: String
)

@JsonClass(generateAdapter = true)
data class GitHubGistRequest(
    val description: String,
    @Json(name = "public") val isPublicGist: Boolean = false,
    val files: Map<String, GitHubGistFile>
)

@JsonClass(generateAdapter = true)
data class GitHubGistResponse(
    val id: String = "",
    @Json(name = "html_url") val htmlUrl: String = ""
)

interface GitHubApiService {
    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") authHeader: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 20
    ): List<GitHubRepoDto>

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getRepoIssues(
        @Header("Authorization") authHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 15
    ): List<GitHubIssueDto>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepoFile(
        @Header("Authorization") authHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): GitHubFileContentDto

    @POST("gists")
    suspend fun createGist(
        @Header("Authorization") authHeader: String,
        @Body body: GitHubGistRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): GitHubGistResponse

    companion object {
        fun create(): GitHubApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GitHubApiService::class.java)
        }
    }
}
