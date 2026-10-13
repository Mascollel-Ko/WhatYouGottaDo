package com.training.trackplanner

import android.content.Intent
import com.training.trackplanner.data.personalized.GenerationDecisionTraceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GenerationDecisionTraceExportTest {
    @Test
    fun userRequestedExportCreatesTheTwoSanitizedUtf8Files() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val providerInfo = requireNotNull(activity.packageManager.resolveContentProvider(
            "${activity.packageName}.fileprovider", android.content.pm.PackageManager.GET_META_DATA
        ))
        val pathsResource = requireNotNull(providerInfo.metaData).getInt("android.support.FILE_PROVIDER_PATHS")
        val parser = activity.resources.getXml(pathsResource)
        val pathTags = buildList {
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) add(parser.name)
            }
        }
        parser.close()
        assertTrue("FileProvider paths metadata must expose a cache root: $pathTags", "cache-path" in pathTags)
        val runId = UUID.randomUUID().toString()
        GenerationDecisionTraceStore.begin(runId, JSONObject()
            .put("goal", "STRENGTH")
            .put("requestedWeeklyTrainingDays", 5)
            .put("email", "must-not-be-exported@example.com"))
        GenerationDecisionTraceStore.record(
            runId, "EMPTY_TRAINING_DAYS", "test/actual result",
            decision = "SCHEDULED_DAYS_EMPTY", reasonCodes = listOf("REQUESTED_DAY_HAS_NO_MATERIALIZED_ITEM"),
            nextStageInputs = JSONObject().put("emptyDays", org.json.JSONArray(listOf(4, 6, 7)))
        )
        GenerationDecisionTraceStore.finish(runId, "FAILED", "GENERATION_FAILED", "test")

        val chooser = requireNotNull(GenerationDecisionTraceExporter.createChooserIntent(activity, runId) { context, file ->
            android.net.Uri.parse("content://${context.packageName}.fileprovider/generation_diagnostics/${file.name}")
        })

        val outputDirectory = File(activity.cacheDir, "generation-diagnostics/$runId")
        val summary = File(outputDirectory, "generation_summary.txt")
        val diagnostic = File(outputDirectory, "generation_diagnostic.json")
        assertTrue(summary.isFile)
        assertTrue(diagnostic.isFile)
        assertTrue(summary.readText(Charsets.UTF_8).contains("8. 빈 운동 요일 발생 원인"))
        val json = diagnostic.readText(Charsets.UTF_8)
        assertTrue(json.contains("SCHEDULED_DAYS_EMPTY"))
        assertTrue(json.contains(runId))
        assertFalse("free-form account fields must not enter an export", json.contains("must-not-be-exported@example.com"))
        assertEquals("generation_summary.txt", summary.name)
        assertEquals("generation_diagnostic.json", diagnostic.name)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val sendIntent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, sendIntent?.action)
        val sharedFiles = sendIntent?.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        assertEquals(2, sharedFiles?.size)
        assertTrue(sharedFiles.orEmpty().all { it.scheme == "content" })
    }
}
