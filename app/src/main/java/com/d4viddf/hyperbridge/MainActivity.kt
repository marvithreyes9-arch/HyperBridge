package com.d4viddf.hyperbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.ui.components.ChangelogDialog
import com.d4viddf.hyperbridge.ui.screens.home.HomeScreen
import com.d4viddf.hyperbridge.ui.screens.onboarding.OnboardingScreen
import com.d4viddf.hyperbridge.ui.screens.settings.InfoScreen
import com.d4viddf.hyperbridge.ui.screens.settings.LicensesScreen
import com.d4viddf.hyperbridge.ui.screens.settings.SetupHealthScreen
import com.d4viddf.hyperbridge.ui.theme.HyperBridgeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HyperBridgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainRootNavigation()
                }
            }
        }
    }
}

// Screen Hierarchy (Depth determines animation direction)
enum class Screen(val depth: Int) {
    ONBOARDING(0),
    HOME(1),
    INFO(2),
    SETUP(3),
    LICENSES(3)
}

@Composable
fun MainRootNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }

    // 1. App Version Info
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) { null }
    }

    val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode?.toInt() ?: 0
    } else {
        packageInfo?.versionCode ?: 0
    }
    val currentVersionName = packageInfo?.versionName ?: "0.1.0"

    // 2. DataStore States
    val isSetupComplete by preferences.isSetupComplete.collectAsState(initial = null)
    val lastSeenVersion by preferences.lastSeenVersion.collectAsState(initial = currentVersionCode)

    // 3. Navigation State
    var currentScreen by remember { mutableStateOf<Screen?>(null) }

    // 4. Changelog Logic
    var showChangelog by remember { mutableStateOf(false) }

    // Initial Routing & Changelog Check
    LaunchedEffect(isSetupComplete, lastSeenVersion) {
        // Check if Onboarding is needed
        if (isSetupComplete == false) {
            currentScreen = Screen.ONBOARDING
        } else if (isSetupComplete == true && currentScreen == null) {
            currentScreen = Screen.HOME
        }

        // Check if Changelog is needed (Only if setup is done AND version increased)
        if (isSetupComplete == true && currentVersionCode > lastSeenVersion) {
            showChangelog = true
        }
    }

    // 5. Back Navigation Logic
    // Enabled only when NOT on Home (Exit app) and NOT on Onboarding (Internal handling)
    BackHandler(enabled = currentScreen != Screen.HOME && currentScreen != Screen.ONBOARDING) {
        currentScreen = when (currentScreen) {
            Screen.SETUP, Screen.LICENSES -> Screen.INFO
            Screen.INFO -> Screen.HOME
            else -> Screen.HOME
        }
    }

    // 6. Render UI
    if (currentScreen == null) {
        // Loading State
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // Main Content with Transitions
        AnimatedContent(
            targetState = currentScreen!!,
            transitionSpec = {
                if (targetState.depth > initialState.depth) {
                    // Navigate Forward: Slide In from Right, Fade In
                    (slideInHorizontally { width -> width } + fadeIn(tween(400)))
                        .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut(tween(400)))
                } else {
                    // Navigate Backward: Slide In from Left, Fade In
                    (slideInHorizontally { width -> -width } + fadeIn(tween(400)))
                        .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut(tween(400)))
                } using SizeTransform(clip = false)
            },
            label = "ScreenTransition"
        ) { target ->
            when (target) {
                Screen.ONBOARDING -> OnboardingScreen {
                    scope.launch {
                        preferences.setSetupComplete(true)
                        // Set version code immediately so they don't see changelog right after onboarding
                        preferences.setLastSeenVersion(currentVersionCode)
                        currentScreen = Screen.HOME
                    }
                }
                Screen.HOME -> HomeScreen(
                    onSettingsClick = { currentScreen = Screen.INFO }
                )
                Screen.INFO -> InfoScreen(
                    onBack = { currentScreen = Screen.HOME },
                    onSetupClick = { currentScreen = Screen.SETUP },
                    onLicensesClick = { currentScreen = Screen.LICENSES }
                )
                Screen.SETUP -> SetupHealthScreen(
                    onBack = { currentScreen = Screen.INFO }
                )
                Screen.LICENSES -> LicensesScreen(
                    onBack = { currentScreen = Screen.INFO }
                )
            }
        }
    }

    // 7. Changelog Modal
    if (showChangelog) {
        ChangelogDialog(
            currentVersionName = currentVersionName,
            // We map the string resource specifically for this version
            changelogText = stringResource(R.string.changelog_0_1_0),
            onDismiss = {
                showChangelog = false
                scope.launch {
                    preferences.setLastSeenVersion(currentVersionCode)
                }
            }
        )
    }
}