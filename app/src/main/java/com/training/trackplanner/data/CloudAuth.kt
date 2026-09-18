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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.TimeoutException
import android.util.Base64

internal enum class CloudAuthStatus { LOGGED_OUT, LOGGED_IN, PROVIDER_UNAVAILABLE, ERROR }

internal data class CloudAuthUiState(
    val status: CloudAuthStatus = CloudAuthStatus.LOGGED_OUT,
    val session: CloudAuthSession? = null,
    val firstLaunchChoiceRequired: Boolean = false,
    val message: String? = null
) {
    /** A temporary auth/network problem must not make a persisted account look logged out. */
    val loggedIn: Boolean get() = session != null && status != CloudAuthStatus.LOGGED_OUT
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

/**
 * Process-wide view of the installation's persisted auth session.
 *
 * The store remains the source of persistence; this manager is the single in-process source
 * consumed by Home, Community, Cloud Backup, and workers. A new process recreates it from the
 * same app-private preferences, so process death and APK updates do not become logouts.
 */
internal class CloudAuthSessionManager internal constructor(
    context: Context,
    private val store: CloudAuthSessionStore = CloudAuthSessionStore(context)
) {
    private val _session = MutableStateFlow(store.read())
    val session: StateFlow<CloudAuthSession?> = _session.asStateFlow()

    fun currentSession(): CloudAuthSession? = _session.value ?: store.read()?.also { _session.value = it }

    fun persist(value: CloudAuthSession) {
        store.write(value)
        _session.value = value
    }

    fun clear() {
        store.clear()
        _session.value = null
    }

    fun firstLaunchChoiceRequired(): Boolean = !store.hasFirstLaunchChoice()
    fun chooseGuest() = store.setFirstLaunchChoice()

    companion object {
        private val lock = Any()
        private val instances = WeakHashMap<Context, CloudAuthSessionManager>()

        fun forApplication(context: Context): CloudAuthSessionManager {
            val application = context.applicationContext
            synchronized(lock) {
                return instances[application] ?: CloudAuthSessionManager(application).also {
                    instances[application] = it
                }
            }
        }
    }
}

internal sealed interface CloudAuthResult {
    data class Success(val session: CloudAuthSession) : CloudAuthResult
    data class Failure(val code: String) : CloudAuthResult
}

internal sealed interface CloudAuthRefreshResult {
    data class Valid(val session: CloudAuthSession) : CloudAuthRefreshResult
    data class TransientFailure(val session: CloudAuthSession, val code: String) : CloudAuthRefreshResult
    data class TerminalFailure(val code: String) : CloudAuthRefreshResult
    data object NoSession : CloudAuthRefreshResult
}

internal data class CloudAuthTokenResponse(val status: Int, val body: String = "")

internal interface CloudAuthTokenClient {
    suspend fun post(config: CloudBackupConfig, grantType: String, body: JSONObject): CloudAuthTokenResponse
}

internal class CloudAuthHttpException(
    val statusCode: Int,
    val responseBody: String
) : IOException("AUTH_HTTP_$statusCode")

private class UrlConnectionCloudAuthTokenClient : CloudAuthTokenClient {
    override suspend fun post(
        config: CloudBackupConfig,
        grantType: String,
        body: JSONObject
    ): CloudAuthTokenResponse = withContext(Dispatchers.IO) {
        val connection = (URL("${config.supabaseUrl}/auth/v1/token?grant_type=$grantType")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", config.publishableKey)
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            CloudAuthTokenResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }
}

internal class CloudAuthRepository(
    private val context: Context,
    private val config: CloudBackupConfig = CloudBackupConfig.fromBuildConfig(),
    private val sessionManager: CloudAuthSessionManager = CloudAuthSessionManager.forApplication(context),
    private val tokenClient: CloudAuthTokenClient = UrlConnectionCloudAuthTokenClient()
) {
    val session: StateFlow<CloudAuthSession?> = sessionManager.session
    fun currentSession(): CloudAuthSession? = sessionManager.currentSession()
    fun firstLaunchChoiceRequired(): Boolean = sessionManager.firstLaunchChoiceRequired()
    fun chooseGuest() = sessionManager.chooseGuest()
    fun logout() = sessionManager.clear()

    suspend fun signInWithGoogle(activity: Activity): CloudAuthResult {
        if (!config.configured) return CloudAuthResult.Failure("CLOUD_NOT_CONFIGURED")
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isBlank()) return CloudAuthResult.Failure("GOOGLE_PROVIDER_NOT_CONFIGURED")
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
            sessionManager.persist(session)
            sessionManager.chooseGuest()
            CloudAuthResult.Success(session)
        } catch (_: NoCredentialException) {
            CloudAuthResult.Failure("NO_GOOGLE_CREDENTIAL")
        } catch (_: Throwable) {
            CloudAuthResult.Failure("AUTHENTICATION_FAILED")
        }
    }

    suspend fun refreshIfNeeded(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): CloudAuthSession? =
        when (val result = refreshIfNeededDetailed(nowEpochSeconds)) {
            is CloudAuthRefreshResult.Valid -> result.session
            is CloudAuthRefreshResult.TransientFailure -> result.session
            is CloudAuthRefreshResult.TerminalFailure,
            CloudAuthRefreshResult.NoSession -> null
        }

    suspend fun refreshIfNeededDetailed(
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000
    ): CloudAuthRefreshResult {
        val current = sessionManager.currentSession() ?: return CloudAuthRefreshResult.NoSession
        if (current.expiresAtEpochSeconds > nowEpochSeconds + 60) {
            return CloudAuthRefreshResult.Valid(current)
        }
        if (current.refreshToken.isBlank()) {
            sessionManager.clear()
            return CloudAuthRefreshResult.TerminalFailure("REFRESH_TOKEN_MISSING")
        }
        return try {
            val response = postToken(
                grantType = "refresh_token",
                body = JSONObject().put("refresh_token", current.refreshToken)
            )
            val refreshed = sessionFromResponse(response, current)
            sessionManager.persist(refreshed)
            CloudAuthRefreshResult.Valid(refreshed)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (isTerminalRefreshFailure(error)) {
                sessionManager.clear()
                CloudAuthRefreshResult.TerminalFailure("SESSION_EXPIRED")
            } else {
                CloudAuthRefreshResult.TransientFailure(current, transientFailureCode(error))
            }
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

    private suspend fun postToken(grantType: String, body: JSONObject): JSONObject {
        val response = tokenClient.post(config, grantType, body)
        if (response.status !in 200..299) {
            throw CloudAuthHttpException(response.status, response.body)
        }
        return JSONObject(response.body)
    }

    private fun isTerminalRefreshFailure(error: Throwable): Boolean {
        val http = generateSequence(error) { it.cause }
            .filterIsInstance<CloudAuthHttpException>()
            .firstOrNull()
            ?: return false
        // Supabase GoTrue uses these explicit error codes/messages for a revoked or
        // otherwise unusable refresh credential. Other 4xx responses stay retryable.
        if (http.statusCode !in 400..499) return false
        val marker = http.responseBody.lowercase()
        return listOf(
            "invalid_grant",
            "refresh_token_not_found",
            "refresh_token_already_used",
            "invalid_refresh_token",
            "invalid refresh token",
            "refresh token not found"
        ).any(marker::contains)
    }

    private fun transientFailureCode(error: Throwable): String = when {
        generateSequence(error) { it.cause }.any { it is UnknownHostException } -> "AUTH_NETWORK_UNAVAILABLE"
        generateSequence(error) { it.cause }.any { it is ConnectException } -> "AUTH_NETWORK_UNAVAILABLE"
        generateSequence(error) { it.cause }.any { it is TimeoutException } -> "AUTH_NETWORK_UNAVAILABLE"
        generateSequence(error) { it.cause }.any { it is IOException } -> "AUTH_NETWORK_UNAVAILABLE"
        else -> "AUTH_UNAVAILABLE"
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
