package com.training.trackplanner.health

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** System-launched permission usage/rationale screen; never a separate launcher. */
class HealthPermissionRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val description = """
            운동·수면 기록 연동

            WhatYouGottaDo는 사용자의 동의를 받은 경우에만 Health Connect에서
            운동 세션의 시작·종료 시각과 수면 시간/수면 단계를 읽습니다.

            운동 시간은 사용자가 확인한 후 기존 운동 기록에 반영됩니다.
            수면은 사용자가 자동 채우기를 활성화했고 수면시간이 비어 있을 때만
            기존 컨디션에 저장합니다. 수동 입력한 값은 덮어쓰지 않습니다.

            심박수 원본, 수면 단계 타임라인 및 Health Connect 기록은 서버에
            업로드하지 않습니다. 기존 백업에는 사용자가 기록한 운동시간과
            수면시간 값만 포함됩니다.

            권한은 언제든 Health Connect 설정에서 철회할 수 있습니다.
            권한을 거절해도 기존 수동 기록 기능은 정상 작동합니다.
        """.trimIndent()
        // Android 16 enforces edge-to-edge for targetSdk 36.
        // Keep the Health Connect permission explanation clear of system bars
        // on all supported Android versions.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val scrollView = ScrollView(this).apply {
            clipToPadding = false
            addView(TextView(this@HealthPermissionRationaleActivity).apply {
                text = description
                textSize = 16f
                setPadding(36, 36, 36, 36)
            })
        }
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val systemInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                systemInsets.left, systemInsets.top,
                systemInsets.right, systemInsets.bottom
            )
            insets
        }
        setContentView(scrollView)
    }
}
