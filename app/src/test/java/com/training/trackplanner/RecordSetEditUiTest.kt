package com.training.trackplanner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.training.trackplanner.data.*
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordSetEditUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `checkbox and delayed input emit disjoint intents from the same old composition`() {
        val entry = WorkoutEntry(id = 1, date = "2026-09-14", exerciseStableKey = "test", exerciseName = "Test", category = "근력운동")
        val snapshot = WorkoutSet(id = 1, entryId = 1, setIndex = 1, reps = 5)
        val edits = mutableListOf<RecordSetEdit>()
        compose.setContent {
            TrainingTrackPlannerTheme {
                WorkoutSetRow(entry, snapshot, listOf(snapshot), showWeight = true,
                    isSportDurationInput = false, canDelete = false, onUpdateSet = edits::add,
                    onDeleteSet = {}, timerState = RestTimerState.Idle, onStopRestTimer = {},
                    onPositiveWeightEdit = { _, _ -> }, onStartRestTimer = { _, _ -> })
            }
        }
        compose.onNode(isToggleable()).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("8")
        compose.runOnIdle {
            assertEquals(setOf(RecordSetField.CONFIRMATION), edits.first().fields)
            assertTrue(edits.first().values.confirmed)
            assertEquals(setOf(RecordSetField.REPS), edits.last().fields)
            assertFalse(edits.last().values.confirmed) // deliberately stale payload
            val stored = edits.fold(snapshot) { current, edit -> edit.applyTo(current) }
            assertTrue(stored.confirmed)
            assertEquals(8, stored.reps)
        }
    }
}
