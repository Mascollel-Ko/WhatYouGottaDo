package com.training.trackplanner.data

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Privileged deletion is only ever handled in the Supabase Edge Function.
 * The APK contains no service-role or R2 secret credentials.
 */
internal class AccountDeletionClient(
    private val config: CloudBackupConfig = CloudBackupConfig.fromBuildConfig(),
    private val transport: CloudHttpTransport = UrlConnectionCloudHttpTransport(config.publishableKey)
) {
    suspend fun delete(freshSession: CloudAuthSession) {
        check(config.configured) { "CLOUD_NOT_CONFIGURED" }
        val response = try {
            transport.postJson(
                "${config.supabaseUrl}/functions/v1/account-delete",
                freshSession,
                JSONObject().put("confirmation", "DELETE_ACCOUNT").toString()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw IllegalStateException("ACCOUNT_DELETE_NETWORK_ERROR")
        }
        val payload = runCatching { JSONObject(response.body) }.getOrNull()
        if (response.status !in 200..299 || payload?.optBoolean("deleted") != true) {
            throw IllegalStateException(
                payload?.optString("error")?.takeIf(String::isNotBlank)
                    ?: "ACCOUNT_DELETION_FAILED"
            )
        }
    }
}

internal object AccountDeletionError {
    fun message(code: String): String = when (code) {
        "LOGIN_REQUIRED" -> "로그인이 만료됐습니다. 다시 로그인해 주세요."
        "GOOGLE_ACCOUNT_MISMATCH" -> "현재 연결된 계정과 다른 Google 계정을 선택했습니다. 원래 계정을 선택해 주세요."
        "GOOGLE_REAUTH_CANCELLED", "NO_GOOGLE_CREDENTIAL" ->
            "Google 계정 확인이 취소됐습니다. 계정은 삭제되지 않았습니다."
        "GOOGLE_REAUTH_FAILED", "AUTHENTICATION_FAILED" ->
            "Google 계정 확인에 실패했습니다. 다시 시도해 주세요."
        "RECENT_GOOGLE_LOGIN_REQUIRED" -> "본인 확인 시간이 만료됐습니다. Google 계정을 다시 확인해 주세요."
        "UPLOAD_URLS_STILL_ACTIVE" ->
            "이전에 발급된 백업 업로드 권한이 아직 유효합니다. 잠시 뒤 다시 확인해 주세요. 계정은 아직 삭제되지 않았습니다."
        "ACCOUNT_DELETE_NOT_READY" ->
            "서버에서 계정 삭제 기능을 준비 중입니다. 현재 계정은 삭제되지 않았습니다."
        "ACCOUNT_DELETE_NETWORK_ERROR" ->
            "네트워크 연결을 확인할 수 없습니다. 삭제 여부를 계정 화면에서 다시 확인해 주세요."
        "CLOUD_NOT_CONFIGURED" ->
            "클라우드 연결이 설정되지 않아 계정 삭제를 요청할 수 없습니다."
        else -> "계정 삭제를 완료하지 못했습니다. 잠시 뒤 다시 시도해 주세요."
    }
}
