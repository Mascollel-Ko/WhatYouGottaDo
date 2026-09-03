package com.training.trackplanner.data

enum class BackupAppMetaAuthority {
    PORTABLE_USER_STATE,
    LOCAL_INFRASTRUCTURE_STATE
}

object BackupAppMetaPolicy {
    private val portableUserStateKeys = setOf(PersonalizedProgramPlanningService.PREFERENCES_KEY)
    private val portableUserStatePrefixes = setOf(PersonalizedProgramPlanningService.DECISION_PREFIX)

    fun authority(key: String): BackupAppMetaAuthority =
        if (key in portableUserStateKeys || portableUserStatePrefixes.any(key::startsWith)) {
            BackupAppMetaAuthority.PORTABLE_USER_STATE
        }
        else BackupAppMetaAuthority.LOCAL_INFRASTRUCTURE_STATE

    fun isSourceOverwriteAllowed(key: String): Boolean =
        authority(key) == BackupAppMetaAuthority.PORTABLE_USER_STATE
}
