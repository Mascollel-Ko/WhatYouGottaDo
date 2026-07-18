package com.training.trackplanner

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class MainActivity : ComponentActivity() {
    private lateinit var restTimerSessionController: RestTimerSessionController
    private val restTimerTargets = MutableSharedFlow<RestTimerTarget>(extraBufferCapacity = 1)

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
    val context = LocalContext.current

    LaunchedEffect(restTimerTargets) {
        restTimerTargets.collect { target ->
            recordTarget = target
            infoRoute = null
            selectedTab = AppTab.Record
        }
    }

    BackHandler(enabled = infoRoute != null || selectedTab != AppTab.Home) {
        val currentRoute = infoRoute
        if (currentRoute != null) {
            infoRoute = currentRoute.parent
        } else {
            selectedTab = AppTab.Home
        }
    }

    Scaffold(
        bottomBar = {
            if (infoRoute == null) {
                AppBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
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
                    onBack = { infoRoute = AppInfoRoute.AppExplanation },
                    onOpenPublicProtocols = {
                        launchPublicProtocolIndex(context::startActivity)
                    }
                )
                null -> when (selectedTab) {
                    AppTab.Home -> HomeScreen(
                        viewModel = viewModel,
                        onNavigate = { selectedTab = it },
                        onOpenAppExplanation = {
                            infoRoute = AppInfoRoute.AppExplanation
                        }
                    )
                    AppTab.Record -> RecordScreen(
                        viewModel = viewModel,
                        restTimerSessionController = restTimerSessionController,
                        target = recordTarget,
                        onOpenPlan = { selectedTab = AppTab.Plan }
                    )
                    AppTab.Plan -> PlanScreen(viewModel) { selectedTab = AppTab.Record }
                    AppTab.Exercise -> ExerciseScreen(viewModel)
                    AppTab.Analysis -> AnalysisScreen(viewModel)
                }
            }
        }
    }
}

@Composable
internal fun AppBottomNavigation(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
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
