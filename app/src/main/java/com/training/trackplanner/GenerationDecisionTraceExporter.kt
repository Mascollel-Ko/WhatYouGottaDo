package com.training.trackplanner

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.training.trackplanner.data.personalized.GenerationDecisionTraceStore
import java.io.File

internal object GenerationDecisionTraceExporter {
    fun share(context: Context, runId: String) {
        runCatching {
            val chooser = createChooserIntent(context, runId)
            if (chooser == null) {
                Toast.makeText(context, "이 실행의 진단 기록을 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
            } else {
                context.startActivity(chooser)
            }
        }.onFailure {
            Toast.makeText(context, "진단 파일을 준비하지 못했습니다.", Toast.LENGTH_LONG).show()
        }
    }

    internal fun createChooserIntent(
        context: Context,
        runId: String,
        uriForFile: (Context, File) -> Uri = { providerContext, file ->
            FileProvider.getUriForFile(providerContext, "${providerContext.packageName}.fileprovider", file)
        }
    ): Intent? {
        val summary = GenerationDecisionTraceStore.summary(runId) ?: return null
        val diagnostic = GenerationDecisionTraceStore.json(runId) ?: return null
        val outputDirectory = File(context.cacheDir, "generation-diagnostics/$runId").apply { mkdirs() }
        val summaryFile = File(outputDirectory, "generation_summary.txt").apply { writeText(summary, Charsets.UTF_8) }
        val diagnosticFile = File(outputDirectory, "generation_diagnostic.json").apply { writeText(diagnostic, Charsets.UTF_8) }
        val uris = arrayListOf(
            uriForFile(context, summaryFile),
            uriForFile(context, diagnosticFile)
        )
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData("Generation diagnostic", arrayOf("*/*"), ClipData.Item(uris.first())).apply {
                addItem(ClipData.Item(uris.last()))
            }
            putExtra(Intent.EXTRA_SUBJECT, "운동 프로그램 생성 진단")
            putExtra(Intent.EXTRA_TEXT, "실행 ID: $runId\n생성된 TXT와 JSON 진단 파일입니다.")
        }
        return Intent.createChooser(send, "생성 진단 내보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
