package com.training.trackplanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BCoachProductionReviewDialogDeviceTest : BCoachReviewLayoutContract() {
    override fun captureReviewScreen(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(100, 5000)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "b-coach-review")
        directory.mkdirs()
        try {
            java.io.File(directory, "$name.png").outputStream().use {
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            image.recycle()
        }
    }
}
