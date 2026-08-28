package com.training.trackplanner

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.training.trackplanner.localization.localizedUiText
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class MainActivity : AppCompatActivity() {
    private lateinit var restTimerSessionController: RestTimerSessionController
    private val restTimerTargets = MutableSharedFlow<RestTimerTarget>(replay = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restTimerSessionController = RestTimerSessionController(this)
        handleRestTimerIntent(intent)
        setContent {
            TrainingTrackPlannerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrainingTrackPlannerApp(
                        restTimerSessionController = restTimerSessionController,
                        restTimerTargets = restTimerTargets
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRestTimerIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        restTimerSessionController.onResume()
    }

    override fun onPause() {
        restTimerSessionController.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        restTimerSessionController.onDestroy()
        super.onDestroy()
    }

    private fun handleRestTimerIntent(intent: android.content.Intent?) {
        RestTimerNavigation.targetFromIntent(intent)?.let { target ->
            restTimerTargets.tryEmit(target)
        }
    }
}

internal enum class AppTab(val label: String, val icon: ImageVector) {
    Home("홈", Icons.Outlined.Home),
    Record("기록", Icons.Outlined.EditNote),
    Plan("계획", Icons.AutoMirrored.Outlined.EventNote),
    Exercise("운동", Icons.Outlined.FitnessCenter),
    Analysis("분석", Icons.Outlined.Analytics)
}

@Composable
internal fun TrainingTrackPlannerApp(
    restTimerSessionController: RestTimerSessionController,
    restTimerTargets: SharedFlow<RestTimerTarget>,
    viewModel: TrainingViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var infoRoute by rememberSaveable { mutableStateOf<AppInfoRoute?>(null) }
    var recordTarget by remember { mutableStateOf<RestTimerTarget?>(null) }
    var recordTargetRequestId by remember { mutableStateOf(0L) }
    var dismissedTimerIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    var onboardingStep by rememberSaveable { mutableStateOf<OnboardingStep?>(null) }
    var onboardingTarget by remember { mutableStateOf<Pair<OnboardingStep, Rect>?>(null) }
    var tutorialApplyRequest by rememberSaveable { mutableStateOf(0) }
    val timerState by restTimerSessionController.state.collectAsState()
    val context = LocalContext.current
    val onboardingStore = remember(context) { OnboardingStore(context) }

    LaunchedEffect(onboardingStore) {
        if (onboardingStep == null && onboardingStore.shouldAutoStart()) {
            onboardingStep = OnboardingStep.HOME_PROGRAM
        }
    }

    LaunchedEffect(restTimerTargets) {
        restTimerTargets.collect { target ->
            recordTargetRequestId += 1
            recordTarget = target.copy(navigationRequestId = recordTargetRequestId)
            infoRoute = null
            selectedTab = AppTab.Record
        }
    }

    BackHandler(enabled = onboardingStep != null || infoRoute != null || selectedTab != AppTab.Home) {
        if (onboardingStep == null) {
            val currentRoute = infoRoute
            if (currentRoute != null) {
                infoRoute = currentRoute.parent
            } else {
                selectedTab = AppTab.Home
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (infoRoute == null) {
                    Column {
                        if (RestTimerForegroundBarPolicy.visible(timerState, dismissedTimerIdentity)) {
                            RestTimerForegroundBar(
                                state = timerState,
                                onOpenTarget = {
                                    recordTargetRequestId += 1
                                    recordTarget = RestTimerForegroundBarPolicy.target(
                                        timerState,
                                        recordTargetRequestId
                                    )
                                    infoRoute = null
                                    selectedTab = AppTab.Record
                                },
                                onDismiss = {
                                    dismissedTimerIdentity =
                                        RestTimerForegroundBarPolicy.presentationIdentity(timerState)
                                }
                            )
                        }
                        AppBottomNavigation(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            onAnalysisTargetPositioned = { bounds ->
                                if (onboardingStep == OnboardingStep.ANALYSIS_TAB) {
                                    onboardingTarget = OnboardingStep.ANALYSIS_TAB to bounds
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (infoRoute) {
                    AppInfoRoute.AppExplanation -> AppExplanationScreen(
                        onBack = { infoRoute = null },
                        onOpenAnalysisGuide = { infoRoute = AppInfoRoute.AnalysisGuide },
                        onOpenCalculationPrinciples = {
                            infoRoute = AppInfoRoute.CalculationPrinciples
                        }
                    )
                    AppInfoRoute.AnalysisGuide -> AnalysisGuideScreen(
                        onBack = { infoRoute = AppInfoRoute.AppExplanation }
                    )
                    AppInfoRoute.CalculationPrinciples -> CalculationPrinciplesScreen(
                        onBack = { infoRoute = AppInfoRoute.AppExplanation }
                    )
                    null -> when (selectedTab) {
                        AppTab.Home -> HomeScreen(
                            viewModel = viewModel,
                            onNavigate = { selectedTab = it },
                            onOpenAppExplanation = {
                                infoRoute = AppInfoRoute.AppExplanation
                            },
                            onProgramTargetPositioned = { bounds ->
                                if (onboardingStep == OnboardingStep.HOME_PROGRAM) {
                                    onboardingTarget = OnboardingStep.HOME_PROGRAM to bounds
                                }
                            }
                        )
                        AppTab.Record -> RecordScreen(
                            viewModel = viewModel,
                            restTimerSessionController = restTimerSessionController,
                            target = recordTarget,
                            onOpenPlan = { selectedTab = AppTab.Plan },
                            showOnboardingWorkoutTarget =
                                onboardingStep == OnboardingStep.RECORD_OVERVIEW,
                            onWorkoutTargetPositioned = { bounds ->
                                if (onboardingStep == OnboardingStep.RECORD_OVERVIEW) {
                                    onboardingTarget = OnboardingStep.RECORD_OVERVIEW to bounds
                                }
                            }
                        )
                        AppTab.Plan -> PlanScreen(
                            viewModel = viewModel,
                            onOpenRecord = {
                                selectedTab = AppTab.Record
                                if (onboardingStep == OnboardingStep.PLAN_APPLY) {
                                    onboardingStep = OnboardingStep.RECORD_OVERVIEW
                                }
                            },
                            tutorialApplyRequest = tutorialApplyRequest,
                            showOnboardingApplyTarget =
                                onboardingStep == OnboardingStep.PLAN_APPLY,
                            onApplyTargetPositioned = { bounds ->
                                if (onboardingStep == OnboardingStep.PLAN_APPLY) {
                                    onboardingTarget = OnboardingStep.PLAN_APPLY to bounds
                                }
                            }
                        )
                        AppTab.Exercise -> ExerciseScreen(viewModel)
                        AppTab.Analysis -> AnalysisScreen(viewModel)
                    }
                }
            }
        }

        onboardingStep?.let { step ->
            OnboardingSpotlight(
                step = step,
                targetBounds = onboardingTarget
                    ?.takeIf { (targetStep) -> targetStep == step }
                    ?.second,
                onTargetClick = {
                    when (step) {
                        OnboardingStep.HOME_PROGRAM -> {
                            selectedTab = AppTab.Plan
                            onboardingStep = OnboardingStep.PLAN_APPLY
                        }
                        OnboardingStep.PLAN_APPLY -> tutorialApplyRequest += 1
                        OnboardingStep.ANALYSIS_TAB -> {
                            selectedTab = AppTab.Analysis
                            onboardingStep = OnboardingStep.ANALYSIS_OVERVIEW
                        }
                        OnboardingStep.RECORD_OVERVIEW,
                        OnboardingStep.ANALYSIS_OVERVIEW -> Unit
                    }
                },
                onNext = {
                    when (step) {
                        OnboardingStep.RECORD_OVERVIEW -> {
                            onboardingStep = OnboardingStep.ANALYSIS_TAB
                        }
                        OnboardingStep.ANALYSIS_OVERVIEW -> {
                            onboardingStore.complete()
                            onboardingStep = null
                        }
                        else -> Unit
                    }
                },
                onSkip = {
                    onboardingStore.skip()
                    onboardingStep = null
                }
            )
        }
    }
}

@Composable
internal fun AppBottomNavigation(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    onAnalysisTargetPositioned: (Rect) -> Unit = {}
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        AppTab.entries.forEach { tab ->
            val localizedLabel = localizedUiText(tab.label)
            NavigationBarItem(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    if (tab == AppTab.Analysis) {
                        onAnalysisTargetPositioned(coordinates.boundsInRoot())
                    }
                },
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = localizedLabel
                    )
                },
                label = { Text(localizedLabel) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
