package com.klisee.reloaded.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.klisee.reloaded.GameRepository
import com.klisee.reloaded.Preferences
import kotlinx.coroutines.launch

/** Screens reached from a tab rather than being tabs themselves. */
private enum class Pushed { About }

@Composable
fun App(repo: GameRepository, prefs: Preferences) {
    var onboarded by remember { mutableStateOf(prefs.onboardingDone) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var pushed by remember { mutableStateOf<Pushed?>(null) }
    // Bumped whenever a tab comes into view, so the pages re-read the game's state. Without it,
    // deleting the game in Ajustes left "Jugar" enabled back in Inicio.
    var revision by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // Swiping between the tabs is more comfortable than aiming at a button, so the three of them
    // are pages of a pager. The bar and the pager are kept in step in both directions.
    val pagerState = rememberPagerState { Tab.entries.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            tab = Tab.entries[page]
            revision++
        }
    }

    // Android 17 does not auto-grant INTERNET, so it has to be requested at runtime or the app
    // cannot open a single socket (socket() fails with EPERM).
    val context = LocalContext.current
    var network by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED)
    }
    val requestNetwork = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        network = granted
    }
    LaunchedEffect(Unit) {
        if (!network) requestNetwork.launch(Manifest.permission.INTERNET)
        // Make sure the bundled MIDI soundfont is next to the game (copied on first run).
        repo.prepare()
    }

    if (!onboarded) {
        Onboarding(
            repo = repo,
            prefs = prefs,
            onFinish = { onboarded = true },
        )
        return
    }

    BackHandler(enabled = pushed != null) { pushed = null }

    when (pushed) {
        Pushed.About -> AboutScreen(onBack = { pushed = null })
        null -> Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (Tab.entries[page]) {
                    Tab.Home -> HomeScreen(
                        repo,
                        revision = revision,
                        networkGranted = network,
                        onRequestNetwork = { requestNetwork.launch(Manifest.permission.INTERNET) },
                        onLog = { scope.launch { pagerState.animateScrollToPage(Tab.Log.ordinal) } },
                        onAbout = { pushed = Pushed.About },
                        onSettings = { scope.launch { pagerState.animateScrollToPage(Tab.Settings.ordinal) } },
                    )
                    Tab.Log -> LogScreen(repo)
                    Tab.Settings -> SettingsScreen(
                        repo,
                        prefs,
                        revision = revision,
                        onLog = { scope.launch { pagerState.animateScrollToPage(Tab.Log.ordinal) } },
                        onAbout = { pushed = Pushed.About },
                    )
                }
            }
            // The bar floats over the pages instead of taking a strip of its own, so nothing is
            // cut off at the bottom and there is no band of a different colour behind it.
            ReloadedBottomNav(
                current = tab,
                onSelect = { target ->
                    // A tap animates across instead of jumping, so it reads the same as a swipe.
                    scope.launch { pagerState.animateScrollToPage(target.ordinal) }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
