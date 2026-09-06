package com.training.trackplanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ProgramProgressionMigration {
    private const val RULE = "rule_version INTEGER NOT NULL, rule_requireCompletion INTEGER NOT NULL, rule_rpePolicy TEXT NOT NULL, rule_rpeThreshold REAL NOT NULL, rule_successesRequired INTEGER NOT NULL, rule_incrementKg REAL, rule_firstFailure TEXT NOT NULL, rule_failuresBeforeDecrease INTEGER NOT NULL, rule_decreasePercent REAL NOT NULL, rule_missingRpe TEXT NOT NULL"

    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE program_progression_tracks (id TEXT NOT NULL PRIMARY KEY, programStableKey TEXT NOT NULL, exerciseStableKey TEXT NOT NULL, label TEXT NOT NULL, role TEXT NOT NULL, roleOverride TEXT NOT NULL, mode TEXT NOT NULL, basePolicy TEXT NOT NULL, anchorSetIndex INTEGER, needsReview INTEGER NOT NULL, $RULE)")
            db.execSQL("CREATE INDEX index_program_progression_tracks_programStableKey ON program_progression_tracks(programStableKey)")
            db.execSQL("""CREATE TABLE program_progression_items (
                programItemId INTEGER NOT NULL PRIMARY KEY, logicalItemId TEXT NOT NULL, trackId TEXT NOT NULL, linkMode TEXT NOT NULL,
                signature_exerciseStableKey TEXT NOT NULL, signature_setCount INTEGER NOT NULL, signature_repsPattern TEXT NOT NULL,
                signature_baseKg REAL, signature_oneRmSnapshotKg REAL, signature_relativeIntensity REAL,
                signature_style TEXT NOT NULL, signature_variant TEXT NOT NULL, signature_trainingSlot TEXT NOT NULL, signature_dayIntensity TEXT NOT NULL,
                signature_basePolicy TEXT NOT NULL, signature_anchorSetIndex INTEGER, signature_plannerRole TEXT NOT NULL,
                FOREIGN KEY(programItemId) REFERENCES training_program_items(id) ON DELETE CASCADE,
                FOREIGN KEY(trackId) REFERENCES program_progression_tracks(id) ON DELETE NO ACTION)""")
            db.execSQL("CREATE INDEX index_program_progression_items_trackId ON program_progression_items(trackId)")
            db.execSQL("CREATE TABLE program_applications (id TEXT NOT NULL PRIMARY KEY, programStableKey TEXT NOT NULL, programName TEXT NOT NULL, startDate TEXT NOT NULL, appliedAt INTEGER NOT NULL)")
            db.execSQL("""CREATE TABLE program_workout_links (
                entryId INTEGER NOT NULL PRIMARY KEY, applicationId TEXT NOT NULL, sourceProgramStableKey TEXT NOT NULL, sourceItemId TEXT NOT NULL,
                programName TEXT NOT NULL, weekNumber INTEGER NOT NULL, dayOfWeek INTEGER NOT NULL, trackId TEXT NOT NULL, trackLabel TEXT NOT NULL,
                sequence INTEGER NOT NULL, role TEXT NOT NULL, mode TEXT NOT NULL, basePolicy TEXT NOT NULL, anchorSetIndex INTEGER, needsReview INTEGER NOT NULL, $RULE,
                FOREIGN KEY(entryId) REFERENCES workout_entries(id) ON DELETE CASCADE,
                FOREIGN KEY(applicationId) REFERENCES program_applications(id) ON DELETE NO ACTION)""")
            db.execSQL("CREATE UNIQUE INDEX index_program_workout_links_applicationId_trackId_sequence ON program_workout_links(applicationId,trackId,sequence)")
            db.execSQL("""CREATE TABLE program_prescription_sets (
                entryId INTEGER NOT NULL, setIndex INTEGER NOT NULL, originalReps INTEGER NOT NULL, originalKg REAL NOT NULL, originalSeconds INTEGER NOT NULL,
                plannedReps INTEGER NOT NULL, plannedKg REAL NOT NULL, plannedSeconds INTEGER NOT NULL, plannedSetIndex INTEGER, originalExists INTEGER NOT NULL, PRIMARY KEY(entryId,setIndex),
                FOREIGN KEY(entryId) REFERENCES workout_entries(id) ON DELETE CASCADE)""")
            db.execSQL("""CREATE TABLE progression_suggestions (
                id TEXT NOT NULL PRIMARY KEY, applicationId TEXT NOT NULL, trackId TEXT NOT NULL, sourceEntryId INTEGER NOT NULL, targetEntryId INTEGER NOT NULL,
                previousActualKg REAL, currentPlanKg REAL, suggestedKg REAL, judgmentRpe REAL, direction TEXT NOT NULL, reasons TEXT NOT NULL,
                evidenceHash TEXT NOT NULL, resolution TEXT NOT NULL, createdAt INTEGER NOT NULL, resolvedAt INTEGER, resolvedKg REAL, $RULE)""")
            db.execSQL("CREATE INDEX index_progression_suggestions_targetEntryId ON progression_suggestions(targetEntryId)")
            db.execSQL("CREATE INDEX index_progression_suggestions_sourceEntryId ON progression_suggestions(sourceEntryId)")
        }
    }
}
