package com.training.trackplanner.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramDraftStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = ProgramDraftStore(context)

    @After
    fun clearDraftFiles() {
        File(context.filesDir, "program_drafts").deleteRecursively()
    }

    @Test
    fun sameDraftIdOverwritesInsteadOfCreatingDuplicate() {
        val draftId = UUID.randomUUID().toString()
        val first = store.save(SavedProgramDraft(draftId = draftId, programName = "시작"))
        assertTrue(first.updatedAt > 0L)

        val second = store.save(first.copy(programName = "수정"))
        assertEquals("수정", store.load(draftId)?.programName)
        assertEquals(1, store.list().size)
        assertEquals(draftId, store.list().single().draftId)
        assertNotNull(second.updatedAt)

        store.delete(draftId)
        assertNull(store.load(draftId))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun skeletonRoundTripsWithoutTouchingCanonicalProgramTables() {
        val request = ProgramSkeletonRequest(
            name = "빈 프로그램",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.0,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = 4
        )
        val skeleton = emptyProgramSkeleton(request, defaultProgramWeekDaySchedule(4, 3))
        val draftId = UUID.randomUUID().toString()

        store.save(SavedProgramDraft(draftId = draftId, programName = "내 초안", skeleton = skeleton))
        val restored = store.load(draftId)
        assertEquals("내 초안", restored?.programName)
        assertEquals(skeleton, restored?.skeleton)
        assertEquals(1, store.list().size)
    }

    @Test
    fun existingProgramDraftIdentityStableAndDifferentAcrossPrograms() {
        val one = ProgramDraftIdentity.forExistingProgram("user_program_1")
        assertEquals(one, ProgramDraftIdentity.forExistingProgram("user_program_1"))
        assertTrue(one != ProgramDraftIdentity.forExistingProgram("user_program_2"))
        assertEquals(120000L, ProgramDraftIdentity.AUTO_SAVE_DEBOUNCE_MILLIS)
    }

    @Test
    fun rejectsUntrustedDraftFilenamesAndUnsupportedSchema() {
        assertThrows(IllegalArgumentException::class.java) {
            store.save(SavedProgramDraft(draftId = "../other", programName = "악성"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.save(SavedProgramDraft(schemaVersion = 999, draftId = UUID.randomUUID().toString(), programName = "구형"))
        }
    }
}
