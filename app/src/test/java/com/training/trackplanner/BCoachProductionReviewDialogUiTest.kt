package com.training.trackplanner

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w320dp-h640dp")
class BCoachProductionReviewDialogUiTest : BCoachReviewLayoutContract()
