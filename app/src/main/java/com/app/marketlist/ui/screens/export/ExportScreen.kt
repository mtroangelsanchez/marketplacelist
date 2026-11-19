package com.app.marketlist.ui.screens.export

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.marketlist.ui.components.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedMessage by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { target: Uri? ->
        val source = (ui.state as? ExportState.Success)?.uri
        if (target != null && source != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(source).use { input ->
                            context.contentResolver.openOutputStream(target).use { output ->
                                requireNotNull(input) { "No se pudo leer el archivo exportado" }
                                requireNotNull(output) { "No se pudo escribir en el destino" }
                                input.copyTo(output)
                            }
                        }
                    }
                    savedMessage = "Archivo guardado en el dispositivo"
                } catch (e: Exception) {
                    viewModel.reportError("No se pudo guardar el archivo: ${e.message ?: "error de escritura"}")
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Exportar a Excel", onBack = onBack)
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lista: ${ui.listLabel}", style = MaterialTheme.typography.titleMedium)

            when (val state = ui.state) {
                ExportState.Idle -> Button(onClick = viewModel::export) { Text("Generar archivo Excel") }

                ExportState.Exporting -> {
                    CircularProgressIndicator()
                    Text("Generando archivo...")
                }

                is ExportState.Success -> {
                    Text("Archivo generado correctamente")
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = XLSX_MIME
                            putExtra(Intent.EXTRA_STREAM, state.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir lista"))
                    }) { Text("Compartir") }
                    OutlinedButton(onClick = { saveLauncher.launch(viewModel.suggestedFileName()) }) {
                        Text("Guardar en el dispositivo")
                    }
                    savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }

                is ExportState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::export) { Text("Reintentar") }
                }
            }
        }
    }
}
