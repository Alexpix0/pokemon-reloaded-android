package com.klisee.reloaded.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klisee.reloaded.Update

/**
 * Offers the newer build, and leaves a way out for anyone who would rather not trust an in-app
 * updater: that last button opens the release page, so the APK can be fetched by hand.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateDialog(
    release: Update.Release,
    busy: Boolean,
    progress: Int,
    problem: String?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        title = { Text("Hay una version nueva") },
        text = {
            Column {
                Text("La version ${release.version} esta disponible. La que tienes es mas antigua.")
                if (busy) {
                    Text(
                        text = "Descargando... $progress%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    LinearWavyProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (problem != null) {
                    Text(
                        text = problem,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                TextButton(onClick = onOpenReleases, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No confio en el autoactualizador",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload, enabled = !busy) {
                Text(if (busy) "Descargando..." else "Descargar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
        },
    )
}
