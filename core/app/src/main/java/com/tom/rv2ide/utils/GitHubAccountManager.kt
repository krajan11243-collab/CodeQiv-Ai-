/*
 * GitHub account connection for CodeQiv.
 * Personal access tokens are verified online and stored with AndroidX encrypted preferences.
 */
package com.tom.rv2ide.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object GitHubAccountManager {
  private const val PREFS = "codeqiv_github_secure"
  private const val KEY_TOKEN = "access_token"
  private const val KEY_USERNAME = "username"

  private fun preferences(context: Context) =
      EncryptedSharedPreferences.create(
          context,
          PREFS,
          MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
          EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
          EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
      )

  fun getConnectedAccount(context: Context): String? =
      runCatching { preferences(context).getString(KEY_USERNAME, null) }.getOrNull()

  fun getAccessToken(context: Context): String? =
      runCatching { preferences(context).getString(KEY_TOKEN, null) }.getOrNull()

  /** Validates the token with GitHub's /user endpoint before saving it. */
  @Throws(IOException::class)
  fun connect(context: Context, token: String): String {
    val cleaned = token.trim()
    if (cleaned.length < 20 || cleaned.any { it.isWhitespace() }) {
      throw IOException("The token format looks invalid.")
    }

    val connection = (URL("https://api.github.com/user").openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 12_000
      readTimeout = 12_000
      setRequestProperty("Accept", "application/vnd.github+json")
      setRequestProperty("Authorization", "Bearer $cleaned")
      setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
      setRequestProperty("User-Agent", "CodeQiv-Android")
    }

    try {
      val status = connection.responseCode
      if (status != HttpURLConnection.HTTP_OK) {
        val message = when (status) {
          HttpURLConnection.HTTP_UNAUTHORIZED -> "GitHub rejected this token. Check it and try again."
          HttpURLConnection.HTTP_FORBIDDEN -> "GitHub rate limit or token permissions prevented connection."
          else -> "GitHub connection failed (HTTP $status)."
        }
        throw IOException(message)
      }
      val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
      val username = JSONObject(body).optString("login").trim()
      if (username.isBlank()) throw IOException("GitHub did not return an account name.")
      preferences(context).edit()
          .putString(KEY_TOKEN, cleaned)
          .putString(KEY_USERNAME, username)
          .apply()
      return username
    } finally {
      connection.disconnect()
    }
  }

  fun disconnect(context: Context) {
    runCatching {
      preferences(context).edit().remove(KEY_TOKEN).remove(KEY_USERNAME).apply()
    }
  }
}
