package com.training.trackplanner.data

enum class BackupAppMetaAuthority {
    PORTABLE_USER_STATE,
    LOCAL_INFRASTRUCTURE_STATE
}

object BackupAppMetaPolicy {
    // No app_meta key is currently part of the portable user-domain contract.
    private val portableUserStateKeys = emptySet<String>()

    fun authority(key: String): BackupAppMetaAuthority =
        if (key in portableUserStateKeys) BackupAppMetaAuthority.PORTABLE_USER_STATE
        else BackupAppMetaAuthority.LOCAL_INFRASTRUCTURE_STATE

    fun isSourceOverwriteAllowed(key: String): Boolean =
        authority(key) == BackupAppMetaAuthority.PORTABLE_USER_STATE
}
