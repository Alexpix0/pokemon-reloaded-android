package com.klisee.reloaded.ui

import android.content.Intent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klisee.reloaded.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the app's own logcat (which includes the emulator's stdout/stderr and native panics),
 * falling back to `files/emulator.log` when logcat is unavailable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(repo: GameRepository, bottomBar: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("Cargando...") }
    var nonce by remember { mutableStateOf(0) }

    LaunchedEffect(nonce) {
        text = withContext(Dispatchers.IO) { readLog(repo) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                actions = {
                    IconButton(onClick = { nonce++ }) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "Compartir logs"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "Compartir") }
                },
            )
        },
        bottomBar = bottomBar,
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).padding(bottom = BottomNavSpace).verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}

private fun readLog(repo: GameRepository): String {
    // `logcat -d` from an app only returns that app's own entries, which is exactly what we want.
    val logcat = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "brief", "-t", "1500")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output.trim()
    }.getOrNull()
    if (!logcat.isNullOrEmpty()) return logcat

    val file = repo.logFile
    if (!file.isFile) return "Todavía no hay logs. Juega una vez para generarlos."
    val text = runCatching { file.readText() }.getOrNull() ?: return "No pude leer los logs."
    return if (text.length > 64_000) "...\n" + text.takeLast(64_000) else text
}
