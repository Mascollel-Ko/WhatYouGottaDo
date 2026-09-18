package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CommunityProgramSnapshotCodecTest {
    private val databases = mutableListOf<TrainingDatabase>()

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        TrainingDatabase::class.java
    ).allowMainThreadQueries().build().also(databases::add)

    @After fun close() = databases.forEach(TrainingDatabase::close)

    @Test fun exportOmitsRoomIdsAndImportCreatesIndependentProgramWithProgression() = runBlocking {
        val source = database()
        source.exerciseDao().insertExercise(Exercise("squat", "Squat", "STRENGTH"))
        val sourceProgramId = source.programDao().insertProgram(TrainingProgram(name = "Public squat", durationDays = 14))
        val itemId = source.programDao().insertProgramItem(
            TrainingProgramItem(
                programId = sourceProgramId,
                weekNumber = 1,
                dayOfWeek = 1,
                orderIndex = 1,
                exerciseStableKey = "squat",
                exerciseName = "Squat",
                category = "STRENGTH",
                setCount = 3,
                reps = 5,
                weightKg = 80.0
            )
        )
        source.programDao().insertProgramItemSets(listOf(TrainingProgramItemSet(programItemId = itemId, setIndex = 1, reps = 5, weightKg = 80.0)))
        val program = source.programDao().findProgram(sourceProgramId)!!
        val track = ProgramProgressionTrack(programStableKey = program.stableKey, exerciseStableKey = "squat", label = "Main")
        source.programProgressionDao().putTrack(track)
        source.programProgressionDao().putItem(
            ProgramProgressionItem(
                programItemId = itemId,
                trackId = track.id,
                signature = ProgressionSignature("squat", 3, "5,5,5", 80.0, null, null)
            )
        )

        val snapshot = CommunityProgramSnapshotCodec.export(source, sourceProgramId)
        val serialized = snapshot.toString()
        assertTrue(!serialized.contains("\"id\""))
        assertTrue(!serialized.contains("\"programId\""))
        assertTrue(!serialized.contains("\"trackId\""))

        val restored = database()
        val restoredId = CommunityProgramSnapshotCodec.import(restored, "public-program-1", snapshot)
        val restoredProgram = restored.programDao().findProgram(restoredId)!!
        assertNotEquals(program.stableKey, restoredProgram.stableKey)
        assertEquals(listOf("squat"), restored.programDao().itemsForProgram(restoredId).map { it.exerciseStableKey })
        assertEquals(1, restored.programDao().programItemSetsForProgram(restoredId).size)
        assertEquals(restoredProgram.stableKey, restored.programProgressionDao().tracks().single().programStableKey)
        assertEquals(restoredId, restored.programDao().itemsForProgram(restoredId).single().programId)
        assertEquals("public-program-1", restored.communityProgramImportDao().findBySourcePublicProgramId("public-program-1")?.sourcePublicProgramId)

        val duplicateId = CommunityProgramSnapshotCodec.import(restored, "public-program-1", snapshot)
        assertNotEquals(restoredId, duplicateId)
        assertEquals(2, restored.communityProgramImportDao().countBySourcePublicProgramId("public-program-1"))
    }

    @Test fun malformedSnapshotWritesNothing() = runBlocking {
        val db = database()
        val invalid = JSONObject().put("schemaVersion", 1).put("program", JSONObject().put("name", "bad")).put(
            "items", org.json.JSONArray().put(JSONObject().put("exerciseStableKey", "squat").put("id", 99))
        )
        assertThrows(IllegalArgumentException::class.java) { runBlocking { CommunityProgramSnapshotCodec.import(db, "public-program-invalid", invalid) } }
        assertEquals(0, db.programDao().countPrograms())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { CommunityProgramSnapshotCodec.import(db, "public-program-invalid", invalid.put("schemaVersion", 99)) } }
        assertEquals(0, db.programDao().countPrograms())
    }
}
