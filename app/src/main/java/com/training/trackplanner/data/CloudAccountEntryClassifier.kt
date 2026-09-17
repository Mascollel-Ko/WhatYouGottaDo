package com.training.trackplanner.data

/** The login boundary is deliberately pure. A caller must decide what to do before it
 * binds local data to an account or attempts a restore. */
internal data class CloudAccountEntrySnapshot(
    val hasMeaningfulLocalData: Boolean,
    val boundUserId: String?,
    val cloudCurrentExists: Boolean,
    /** False when the runtime has not queried the authenticated user's CURRENT metadata. */
    val cloudCurrentKnown: Boolean = true
)

internal enum class CloudAccountEntryAction {
    ENABLE_CLOUD,
    AUTO_RESTORE_CURRENT,
    ADOPT_GUEST_LOCAL_DATA,
    REQUIRE_GUEST_CLOUD_COMPARISON,
    RESUME_SAME_ACCOUNT,
    REQUIRE_ACCOUNT_ARCHIVE
}

internal object CloudAccountEntryClassifier {
    fun classify(snapshot: CloudAccountEntrySnapshot, targetUserId: String): CloudAccountEntryAction {
        require(targetUserId.isNotBlank()) { "A verified Supabase user id is required." }
        val bound = snapshot.boundUserId?.takeIf(String::isNotBlank)
        if (bound != null && !bound.equals(targetUserId, ignoreCase = true)) {
            // Account archive and semantic comparison are intentionally a separate guarded
            // operation. Never bind account B to account A's active Room rows.
            return CloudAccountEntryAction.REQUIRE_ACCOUNT_ARCHIVE
        }
        if (bound != null) return CloudAccountEntryAction.RESUME_SAME_ACCOUNT
        if (snapshot.hasMeaningfulLocalData && !snapshot.cloudCurrentKnown) {
            return CloudAccountEntryAction.REQUIRE_GUEST_CLOUD_COMPARISON
        }
        return when {
            !snapshot.hasMeaningfulLocalData && snapshot.cloudCurrentExists ->
                CloudAccountEntryAction.AUTO_RESTORE_CURRENT
            !snapshot.hasMeaningfulLocalData -> CloudAccountEntryAction.ENABLE_CLOUD
            snapshot.cloudCurrentExists -> CloudAccountEntryAction.REQUIRE_GUEST_CLOUD_COMPARISON
            else -> CloudAccountEntryAction.ADOPT_GUEST_LOCAL_DATA
        }
    }
}
