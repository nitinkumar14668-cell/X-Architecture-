package com.example.repository

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GitHubProfile(
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val bio: String
)

class GitHubService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

    /**
     * Checks token validity and fetches profile details of the connected user.
     */
    fun checkConnection(token: String): Result<GitHubProfile> {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val username = json.optString("login", "")
                    val displayName = json.optString("name", username)
                    val avatarUrl = json.optString("avatar_url", "")
                    val bio = json.optString("bio", "No bio provided")
                    Result.success(GitHubProfile(username, displayName, avatarUrl, bio))
                } else {
                    Result.failure(IOException("GitHub API returned code ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches repositories matching the user token.
     */
    fun fetchRepositories(token: String): Result<List<String>> {
        val request = Request.Builder()
            .url("https://api.github.com/user/repos?per_page=100&sort=updated")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val jsonArray = JSONArray(bodyStr)
                    val repoNames = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        val repoObj = jsonArray.getJSONObject(i)
                        repoNames.add(repoObj.getString("name"))
                    }
                    Result.success(repoNames.sortedWith(String.CASE_INSENSITIVE_ORDER))
                } else {
                    Result.failure(IOException("Failed to fetch repositories (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if a file exists on GitHub and retrieves its SHA hash blob.
     * Returns null if file doesn't exist.
     */
    private fun getFileSha(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        repoPath: String
    ): String? {
        val encodedPath = repoPath.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        val url = "https://api.github.com/repos/$owner/$repo/contents/$encodedPath?ref=$branch"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    json.optString("sha", null)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Uploads/Pushes single file content to a specific GitHub repository.
     */
    fun pushFile(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        repoPath: String,
        fileBytes: ByteArray,
        commitMessage: String
    ): Result<String> {
        return try {
            // Step 1: Query for file SHA (important to support updates/modifications)
            val sha = getFileSha(token, owner, repo, branch, repoPath)

            // Step 2: Base64 encode file content without line breaks
            val base64Content = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

            // Step 3: Build PUT request payload
            val root = JSONObject().apply {
                put("message", commitMessage)
                put("content", base64Content)
                put("branch", branch)
                if (sha != null) {
                    put("sha", sha)
                }
            }

            val requestBody = root.toString().toRequestBody(jsonMediaType)
            val encodedPath = repoPath.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
            val putUrl = "https://api.github.com/repos/$owner/$repo/contents/$encodedPath"

            val request = Request.Builder()
                .url(putUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(bodyStr)
                    val commitHtmlUrl = jsonResponse.getJSONObject("commit").optString("html_url", "")
                    Result.success(commitHtmlUrl)
                } else {
                    val errorDetail = response.body?.string() ?: ""
                    val errorMsg = try {
                        JSONObject(errorDetail).optString("message", "Unknown error")
                    } catch (e: Exception) {
                        "HTTP Status ${response.code}"
                    }
                    Result.failure(IOException("Upload failed: $errorMsg"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
