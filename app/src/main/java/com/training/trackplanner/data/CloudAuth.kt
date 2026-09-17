package com.training.trackplanner.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.training.trackplanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import android.util.Base64

internal enum class CloudAuthStatus { LOGGED_OUT, LOGGED_IN, PROVIDER_UNAVAILABLE, ERROR }

internal data class CloudAuthUiState(
    val status: CloudAuthStatus = CloudAuthStatus.LOGGED_OUT,
    val session: CloudAuthSession? = null,
    val firstLaunchChoiceRequired: Boolean = false,
    val message: String? = null
) {
    val loggedIn: Boolean get() = status == CloudAuthStatus.LOGGED_IN && session != null
}

/** Installation-local auth storage. Tokens stay in app-private preferences and are never
 * included in canonical backups or diagnostic reports. */
internal class CloudAuthSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_auth_session", Context.MODE_PRIVATE)

    fun read(): CloudAuthSession? {
        val userId = preferences.getString(KEY_USER_ID, null)?.takeIf(String::isNotBlank) ?: return null
        val access = preferences.getString(KEY_ACCESS_TOKEN, null)?.takeIf(String::isNotBlank) ?: return null
        return CloudAuthSession(
            userId = userId,
            accessToken = access,
            refreshToken = preferences.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            expiresAtEpochSeconds = preferences.getLong(KEY_EXPIRES_AT, 0L),
            displayEmail = preferences.getString(KEY_EMAIL, null)
        )
    }

    fun write(session: CloudAuthSession) {
        preferences.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtEpochSeconds)
            .putString(KEY_EMAIL, session.displayEmail)
            .apply()
    }

    /** Logout removes only credentials; the installation's first-launch choice remains set. */
    fun clear() = preferences.edit()
        .remove(KEY_USER_ID)
        .remove(KEY_ACCESS_TOKEN)
        .remove(KEY_REFRESH_TOKEN)
        .remove(KEY_EXPIRES_AT)
        .remove(KEY_EMAIL)
        .apply()

    fun hasFirstLaunchChoice(): Boolean = preferences.getBoolean(KEY_CHOICE_SET, false)
    fun setFirstLaunchChoice() = preferences.edit().putBoolean(KEY_CHOICE_SET, true).apply()

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_EMAIL = "email"
        const val KEY_CHOICE_SET = "first_launch_choice_set"
    }
}

internal sealed interface CloudAuthResult {
    data class Success(val session: CloudAuthSession) : CloudAuthResult
    data class Failure(val code: String) : CloudAuthResult
}

internal class CloudAuthRepository(
    private val context: Context,
    private val config: CloudBackupConfig = CloudBackupConfig.fromBuildConfig(),
    private val store: CloudAuthSessionStore = CloudAuthSessionStore(context)
) {
    fun currentSession(): CloudAuthSession? = store.read()
    fun firstLaunchChoiceRequired(): Boolean = !store.hasFirstLaunchChoice()
    fun chooseGuest() = store.setFirstLaunchChoice()
    fun logout() = store.clear()

    suspend fun signInWithGoogle(activity: Activity): CloudAuthResult {
        if (!config.configured) return CloudAuthResult.Failure("PROVIDER_NOT_CONFIGURED")
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) return CloudAuthResult.Failure("PROVIDER_NOT_CONFIGURED")
        return try {
            val rawNonce = secureNonce()
            val authorizedCredential = try {
                requestCredential(activity, webClientId, sha256Hex(rawNonce), authorizedOnly = true)
            } catch (_: NoCredentialException) {
                null
            }
            val credential = authorizedCredential
                ?: requestCredential(activity, webClientId, sha256Hex(rawNonce), authorizedOnly = false)
                ?: return CloudAuthResult.Failure("NO_GOOGLE_CREDENTIAL")
            val googleToken = (credential as? GoogleIdTokenCredential)?.idToken
                ?: return CloudAuthResult.Failure("INVALID_GOOGLE_CREDENTIAL")
            val session = exchangeIdToken(googleToken, rawNonce)
            store.write(session)
            store.setFirstLaunchChoice()
            CloudAuthResult.Success(session)
        } catch (_: NoCredentialException) {
            CloudAuthResult.Failure("NO_GOOGLE_CREDENTIAL")
        } catch (_: Throwable) {
            CloudAuthResult.Failure("AUTHENTICATION_FAILED")
        }
    }

    suspend fun refreshIfNeeded(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): CloudAuthSession? {
        val current = store.read() ?: return null
        if (current.expiresAtEpochSeconds > nowEpochSeconds + 60) return current
        if (current.refreshToken.isBlank()) {
            store.clear()
            return null
        }
        return runCatching {
            val response = postToken(
                grantType = "refresh_token",
                body = JSONObject().put("refresh_token", current.refreshToken)
            )
            val refreshed = sessionFromResponse(response, current)
            store.write(refreshed)
            refreshed
        }.getOrNull() ?: run {
            store.clear()
            null
        }
    }

    private suspend fun requestCredential(
        activity: Activity,
        webClientId: String,
        hashedNonce: String,
        authorizedOnly: Boolean
    ): GoogleIdTokenCredential? {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(authorizedOnly)
            .setNonce(hashedNonce)
            .build()
        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        )
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) return null
        return GoogleIdTokenCredential.createFrom(credential.data)
    }

    private suspend fun exchangeIdToken(idToken: String, rawNonce: String): CloudAuthSession = withContext(Dispatchers.IO) {
        val response = postToken(
            grantType = "id_token",
            body = JSONObject()
                .put("provider", "google")
                .put("id_token", idToken)
                .put("nonce", rawNonce)
        )
        sessionFromResponse(response, null)
    }

    private fun postToken(grantType: String, body: JSONObject): JSONObject {
        val connection = (URL("${config.supabaseUrl}/auth/v1/token?grant_type=$grantType").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", config.publishableKey)
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("AUTH_${status}")
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun sessionFromResponse(response: JSONObject, previous: CloudAuthSession?): CloudAuthSession {
        val access = response.optString("access_token").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("AUTH_RESPONSE_INVALID")
        val user = response.optJSONObject("user")
        val userId = user?.optString("id").orEmpty().takeIf(String::isNotBlank)
            ?.let { value -> runCatching { UUID.fromString(value).toString() }.getOrNull() }
            ?: throw IllegalStateException("AUTH_RESPONSE_INVALID")
        return CloudAuthSession(
            userId = userId,
            accessToken = access,
            refreshToken = response.optString("refresh_token").ifBlank { previous?.refreshToken.orEmpty() },
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + response.optLong("expires_in", 3600),
            displayEmail = user?.optString("email")?.takeIf(String::isNotBlank) ?: previous?.displayEmail
        )
    }

    private fun secureNonce(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }
        .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
