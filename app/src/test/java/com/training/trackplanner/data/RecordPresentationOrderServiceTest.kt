package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecordPresentationOrderServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun `manual reorder persists only display order and marks the date`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val before = db.workoutDao().entriesWithSets(DATE).associateBy { it.entry.id }

        assertTrue(service(db).reorder(DATE, listOf(ids[1], ids[2], ids[0])))

        val after = RecordEntryOrdering.ordered(db.workoutDao().entriesWithSets(DATE))
        assertEquals(listOf(ids[1], ids[2], ids[0]), after.map { it.entry.id })
        after.forEachIndexed { index, record ->
            val original = checkNotNull(before[record.entry.id])
            assertEquals(original.entry.copy(displayOrder = index + 1), record.entry)
            assertEquals(original.sets, record.sets)
        }
        assertEquals(
            RecordManualOrderPolicy.markerValue(ids),
            db.appMetaDao().value(RecordManualOrderPolicy.key(DATE))
        )
        assertTrue(db.strengthPosteriorDao().allEvents().isEmpty())
    }

    @Test
    fun `first to last and last to middle orders are accepted while invalid graph is rejected`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val presentation = service(db)

        assertTrue(presentation.reorder(DATE, listOf(ids[1], ids[2], ids[0])))
        assertTrue(presentation.reorder(DATE, listOf(ids[1], ids[0], ids[2])))
        assertFalse(presentation.reorder(DATE, listOf(ids[0], ids[1], 999L)))
        assertEquals(
            listOf(ids[1], ids[0], ids[2]),
            RecordEntryOrdering.ordered(db.workoutDao().entriesWithSets(DATE)).map { it.entry.id }
        )
    }

    @Test
    fun `first confirmation inserts into manual order without a marker veto`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val manualOrder = listOf(ids[2], ids[0], ids[1])
        assertTrue(service(db).reorder(DATE, manualOrder))
        val targetSet = db.workoutDao().setsForEntry(ids[1]).single()

        RecordMutationService(
            db = db,
            exerciseDao = db.exerciseDao(),
            workoutDao = db.workoutDao(),
            appMetaDao = db.appMetaDao()
        ).updateSet(targetSet.copy(confirmed = true))

        assertEquals(
            listOf(ids[1], ids[2], ids[0]),
            RecordEntryOrdering.ordered(db.workoutDao().entriesWithSets(DATE)).map { it.entry.id }
        )
    }

    @Test fun `manual Pull-up before Squat survives Curl first confirmation with content unchanged`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db, listOf("Squat", "Pull-up", "Push-up", "Curl"))
        val dao = db.workoutDao()
        val mutation = RecordMutationService(db, db.exerciseDao(), dao, appMetaDao = db.appMetaDao())
        for (id in ids.take(2)) mutation.updateSet(dao.setsForEntry(id).single().copy(confirmed=true))
        // Explicit unequal historical timestamps make a chronology-sort regression deterministic.
        ids.take(2).forEachIndexed { index,id -> dao.updateEntry(dao.findEntryById(id)!!.copy(firstConfirmedAt=100L+index)) }
        service(db).reorder(DATE,listOf(ids[1],ids[0],ids[2],ids[3]))
        val before = dao.entriesWithSets(DATE).associateBy { it.entry.id }
        val target = dao.setsForEntry(ids[3]).single()
        mutation.updateSet(target.copy(confirmed=true))
        val after = RecordEntryOrdering.ordered(dao.entriesWithSets(DATE))
        assertEquals(listOf(ids[1],ids[0],ids[3],ids[2]),after.map { it.entry.id })
        after.forEach { record ->
            val original = before.getValue(record.entry.id)
            if(record.entry.id==ids[3]) {
                assertEquals(original.entry,record.entry.copy(displayOrder=original.entry.displayOrder,
                    firstConfirmedAt=original.entry.firstConfirmedAt,completedAt=original.entry.completedAt,performedAt=original.entry.performedAt))
                assertEquals(original.sets.map { it.copy(confirmed=true) },record.sets)
            } else {
                assertEquals(original.entry,record.entry.copy(displayOrder=original.entry.displayOrder))
                assertEquals(original.sets,record.sets)
            }
        }
        assertTrue(db.strengthPosteriorDao().allEvents().isEmpty())
    }

    @Test fun `manual order after all entries start survives edit unconfirm and reconfirm`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val dao = db.workoutDao()
        val mutation = RecordMutationService(db,db.exerciseDao(),dao,appMetaDao=db.appMetaDao())
        for(id in ids) mutation.updateSet(dao.setsForEntry(id).single().copy(confirmed=true))
        val manual = listOf(ids[2],ids[0],ids[1])
        service(db).reorder(DATE,manual)
        val before = dao.entriesWithSets(DATE).associate { it.entry.id to (it.entry.displayOrder to it.entry.firstConfirmedAt) }
        val target = dao.setsForEntry(ids[0]).single()
        mutation.updateSet(target.copy(reps=12))
        mutation.updateSet(target.copy(confirmed=false))
        mutation.updateSet(target.copy(confirmed=true))
        assertEquals(before,dao.entriesWithSets(DATE).associate { it.entry.id to (it.entry.displayOrder to it.entry.firstConfirmedAt) })
        assertEquals(manual,RecordEntryOrdering.ordered(dao.entriesWithSets(DATE)).map { it.entry.id })
    }

    @Test fun `C then B starts preserve chronology and later sets do not reorder`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val mutation = RecordMutationService(db, db.exerciseDao(), db.workoutDao(), appMetaDao = db.appMetaDao())
        suspend fun confirm(id: Long) { mutation.updateSet(db.workoutDao().setsForEntry(id).first().copy(confirmed = true)) }
        confirm(ids[2])
        val c = db.workoutDao().findEntryById(ids[2])!!
        db.workoutDao().updateEntry(c.copy(firstConfirmedAt = 1L))
        confirm(ids[1])
        assertEquals(listOf(ids[2], ids[1], ids[0]), RecordEntryOrdering.ordered(db.workoutDao().entriesWithSets(DATE)).map { it.entry.id })
        service(db).reorder(DATE, ids)
        val before = db.workoutDao().entriesWithSets(DATE)
        val b = db.workoutDao().findEntryById(ids[1])!!
        mutation.addSet(b)
        mutation.updateSet(db.workoutDao().setsForEntry(ids[1]).last().copy(confirmed = true))
        mutation.updateSet(db.workoutDao().setsForEntry(ids[1]).first().copy(confirmed = true, reps = 11))
        assertEquals(ids, RecordEntryOrdering.ordered(db.workoutDao().entriesWithSets(DATE)).map { it.entry.id })
        assertEquals(b.firstConfirmedAt, db.workoutDao().findEntryById(ids[1])!!.firstConfirmedAt)
        val unstarted = before.first { it.entry.id == ids[0] }
        assertEquals(unstarted, db.workoutDao().entriesWithSets(DATE).first { it.entry.id == ids[0] })
    }

    @Test fun `one confirmation survives delayed stale field callbacks and reload`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val dao = db.workoutDao()
        val mutation = RecordMutationService(db, db.exerciseDao(), dao)
        val stale = dao.setsForEntry(ids[2]).single()
        mutation.addSet(dao.findEntryById(ids[2])!!)
        val untouched = dao.setsForEntry(ids[2]).last()
        mutation.updateSet(stale.copy(confirmed = true).edit(RecordSetField.CONFIRMATION))
        // A delayed pre-confirmation input snapshot must not undo the one checkbox action.
        mutation.updateSet(stale.copy(reps = 0).edit(RecordSetField.REPS))
        val reloaded = RecordEntryOrdering.ordered(dao.entriesWithSets(DATE))
        assertEquals(listOf(ids[2], ids[0], ids[1]), reloaded.map { it.entry.id })
        assertEquals(stale.copy(confirmed = true, reps = 0), dao.findSetById(stale.id))
        assertEquals(untouched, dao.findSetById(untouched.id))
        assertTrue(reloaded.first().entry.firstConfirmedAt != null)
    }

    @Test fun `already performed A stays before C and explicit unconfirm survives fresh reload`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val dao = db.workoutDao()
        val mutation = RecordMutationService(db, db.exerciseDao(), dao)
        val a = dao.setsForEntry(ids[0]).single()
        val c = dao.setsForEntry(ids[2]).single()
        mutation.updateSet(a.copy(confirmed = true).edit(RecordSetField.CONFIRMATION))
        assertEquals(ids, RecordEntryOrdering.ordered(dao.entriesWithSets(DATE)).map { it.entry.id })
        assertTrue(dao.findSetById(a.id)!!.confirmed)
        mutation.updateSet(c.copy(confirmed = true).edit(RecordSetField.CONFIRMATION))
        val order = listOf(ids[0], ids[2], ids[1])
        assertEquals(order, RecordEntryOrdering.ordered(dao.entriesWithSets(DATE)).map { it.entry.id })
        assertTrue(dao.findSetById(c.id)!!.confirmed)
        mutation.updateSet(c.copy(confirmed = false).edit(RecordSetField.CONFIRMATION))
        assertFalse(dao.findSetById(c.id)!!.confirmed)
        assertEquals(order, RecordEntryOrdering.ordered(dao.entriesWithSets(DATE)).map { it.entry.id })
        assertTrue(service(db).reorder(DATE, ids.reversed()))
        mutation.updateSet(c.copy(confirmed = true).edit(RecordSetField.CONFIRMATION))
        assertEquals(ids.reversed(), RecordEntryOrdering.ordered(dao.entriesWithSets(DATE)).map { it.entry.id })
    }

    @Test fun `each unrelated field intent preserves confirmation and all other latest fields`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val dao = db.workoutDao()
        val mutation = RecordMutationService(db, db.exerciseDao(), dao)
        val stale = dao.setsForEntry(ids.last()).single()
        mutation.updateSet(stale.copy(confirmed = true).edit(RecordSetField.CONFIRMATION))
        val edits = listOf(
            stale.copy(reps = 12).edit(RecordSetField.REPS),
            stale.copy(weightKg = 72.5, manualWeight = true).edit(RecordSetField.WEIGHT),
            stale.copy(seconds = 45).edit(RecordSetField.DURATION),
            stale.copy(rpe = null).edit(RecordSetField.RPE),
            stale.copy(restSecondsOverride = null).edit(RecordSetField.REST)
        )
        var expected = stale.copy(confirmed = true)
        for (edit in edits) {
            mutation.updateSet(edit)
            expected = edit.applyTo(expected)
            assertEquals(expected, dao.findSetById(stale.id))
            assertTrue(dao.findSetById(stale.id)!!.confirmed)
        }
        // A delayed confirmation also cannot restore outdated numeric values.
        mutation.updateSet(stale.copy(confirmed = false).edit(RecordSetField.CONFIRMATION))
        assertEquals(expected.copy(confirmed = false), dao.findSetById(stale.id))
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun service(db: TrainingDatabase) =
        RecordPresentationOrderService(db, db.workoutDao(), db.appMetaDao())

    private suspend fun addEntries(db: TrainingDatabase, names: List<String> = listOf("스쿼트", "벤치프레스", "데드리프트")): List<Long> =
        names.mapIndexed { index, name ->
            val stableKey = "exercise_$index"
            db.exerciseDao().insertExercise(
                Exercise(stableKey = stableKey, name = name, category = "근력운동", mode = "무게*횟수")
            )
            val entryId = db.workoutDao().insertEntry(
                WorkoutEntry(
                    date = DATE,
                    exerciseStableKey = stableKey,
                    exerciseName = name,
                    category = "근력운동",
                    notes = "note-$index",
                    rpe = 7.0 + index,
                    maxReps = 10 + index,
                    createdAt = 1_000L + index,
                    completedAt = null,
                    displayOrder = index + 1,
                    firstConfirmedAt = null,
                    performedAt = null,
                    backupSourceId = "source-$index"
                )
            )
            db.workoutDao().insertSet(
                WorkoutSet(
                    entryId = entryId,
                    setIndex = 1,
                    reps = 5 + index,
                    weightKg = 50.0 + index,
                    rpe = 8.0,
                    restSecondsOverride = 90
                )
            )
            entryId
        }

    private companion object {
        const val DATE = "2026-08-22"
    }
}
