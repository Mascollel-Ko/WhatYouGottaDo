package com.training.trackplanner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal object DocumentationLinks {
    const val HOME = "https://github.com/Mascollel-Ko/WhatYouGottaDo-Docs"

    val ANALYSIS_OVERVIEW = document("docs", "02_\uAE30\uB2A5", "\uBD84\uC11D.md")
    val OFI_GUIDE = document("docs", "03_\uACB0\uACFC_\uC77D\uB294\uBC95", "OFI.md")
    val STRENGTH_ESTIMATE_GUIDE = document(
        "docs",
        "03_\uACB0\uACFC_\uC77D\uB294\uBC95",
        "\uADFC\uB825_\uC218\uD589\uB2A5\uB825_\uCD94\uC815.md"
    )
    val CONNECTIVE_TISSUE_GUIDE = document(
        "docs",
        "03_\uACB0\uACFC_\uC77D\uB294\uBC95",
        "\uC5F0\uACB0\uC870\uC9C1_\uC0AC\uC6A9\uB7C9.md"
    )
    val BADMINTON_GUIDE = document(
        "docs",
        "03_\uACB0\uACFC_\uC77D\uB294\uBC95",
        "\uBC30\uB4DC\uBBFC\uD134.md"
    )
    val LAB_STRENGTH_PERFORMANCE_GUIDE =
        document(
            "docs",
            "03_\uACB0\uACFC_\uC77D\uB294\uBC95",
            "\uC2E4\uD5D8\uC2E4",
            "\uADFC\uB825\uC6B4\uB3D9_\uD37C\uD3EC\uBA3C\uC2A4.md"
        )

    val all: List<String> = listOf(
        HOME,
        ANALYSIS_OVERVIEW,
        OFI_GUIDE,
        STRENGTH_ESTIMATE_GUIDE,
        CONNECTIVE_TISSUE_GUIDE,
        BADMINTON_GUIDE,
        LAB_STRENGTH_PERFORMANCE_GUIDE
    )

    private fun document(vararg pathSegments: String): String = Uri.Builder()
        .scheme("https")
        .authority("github.com")
        .appendPath("Mascollel-Ko")
        .appendPath("WhatYouGottaDo-Docs")
        .appendPath("blob")
        .appendPath("main")
        .apply { pathSegments.forEach { segment -> appendPath(segment) } }
        .build()
        .toString()
}

internal fun launchPublicDocumentation(
    url: String,
    startActivity: (Intent) -> Unit
): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

@Composable
internal fun DocumentationAction(
    url: String,
    testTag: String,
    label: String = "자세히 알아보기"
) {
    val context = LocalContext.current
    var browserUnavailable by rememberSaveable(url) { mutableStateOf(false) }
    TextButton(
        modifier = Modifier.testTag(testTag),
        onClick = {
            browserUnavailable = !launchPublicDocumentation(url, context::startActivity)
        }
    ) {
        Text(label)
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null
        )
    }
    if (browserUnavailable) {
        Text(
            text = stringResource(R.string.public_protocol_browser_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
