package com.training.trackplanner.data

/** Full replacement, not merge. Local infrastructure and installation metadata stay local.
 * Keep this explicit table audit in step with future Room/user-data additions.
 */
internal suspend fun clearLocalRecoveryUserData(db: TrainingDatabase) {
    check(db.inTransaction())
    val tables = listOf(
        "workout_sets",
        "program_progression_items",
        "program_progression_tracks",
        "program_workout_links",
        "program_applications",
        "program_prescription_sets",
        "progression_suggestions",
        "workout_entries",
        "training_program_item_sets",
        "training_program_items",
        "exercise_metadata_user_overrides",
        "exercise_training_role_relations",
        "exercise_program_slot_capability_relations",
        "exercises",
        "daily_metrics",
        "daily_check_ins",
        "smash_speed_records",
        "training_programs",
        "training_program_tombstones",
        "exercise_identity_migration_issues",
        "initial_user_profiles",
        "runtime_exercise_metadata",
        "strength_posterior_events",
        "strength_posterior_history",
        "strength_posterior_model_state",
        "strength_curve_posteriors",
        "strength_posterior_evidence",
        "strength_model_revisions",
        "strength_exercise_performance_state",
        "strength_exercise_performance_history",
        "strength_proxy_transfer_history"
    )
    tables.forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM `$it`") }
    db.appMetaDao().all().filter { BackupAppMetaPolicy.isSourceOverwriteAllowed(it.key) }
        .forEach { db.appMetaDao().delete(it.key) }
}
