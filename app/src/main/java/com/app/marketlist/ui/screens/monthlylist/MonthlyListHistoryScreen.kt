package com.app.marketlist.ui.screens.monthlylist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.marketlist.domain.usecase.ListNameSuggester
import com.app.marketlist.ui.components.ScreenHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyListHistoryScreen(
    onExport: (Long) -> Unit,
    onListClick: (Long) -> Unit,
    onNewProduct: () -> Unit,
    onProductClick: (Long) -> Unit,
    onScanTicket: () -> Unit,
    onScanList: () -> Unit,
    viewModel: MonthlyListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val excelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importExcel)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Listas mensuales")

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::startCreate) { Text("Crear nueva lista") }
                OutlinedButton(onClick = onScanTicket) { Text("Leer ticket") }
                OutlinedButton(onClick = onScanList) { Text("Leer lista") }
                OutlinedButton(onClick = {
                    excelPicker.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                            "*/*"
                        )
                    )
                }) { Text("Importar Excel") }
            }
            state.message?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = viewModel::consumeMessage) { Text("OK") }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            item {
                Text("Historial", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
            }
            items(state.lists, key = { "list_${it.id}" }) { list ->
                val isActive = list.id == state.activeList?.id
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onListClick(list.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(list.label, style = MaterialTheme.typography.titleMedium)
                            if (isActive) Text("Lista activa", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onExport(list.id) }) { Text("Exportar") }
                    }
                }
            }

            if (state.activeList != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Productos de ${state.activeList?.label}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showAddDialog = true }) { Text("Agregar") }
                    }
                }
                items(state.activeProducts, key = { "product_${it.id}" }) { product ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onProductClick(product.id) }, modifier = Modifier.weight(1f)) {
                            Text(product.name, modifier = Modifier.fillMaxWidth())
                        }
                        IconButton(onClick = { viewModel.removeProduct(product.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar de la lista")
                        }
                    }
                }
            }
        }
    }

    state.newListForm?.let { form ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCreate,
            title = { Text("Nueva lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = viewModel::onNewListNameChange,
                        label = { Text("Nombre de la lista") },
                        singleLine = true
                    )
                    Text("Período: ${ListNameSuggester.periodLabel(form.month, form.year)}")
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmCreate) { Text("Crear") } },
            dismissButton = { TextButton(onClick = viewModel::dismissCreate) { Text("Cancelar") } }
        )
    }
    state.excelResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissExcelResult,
            title = { Text("Importación de Excel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Creados: ${result.created}, saltados: ${result.skipped}, " +
                            "tiendas creadas: ${result.storesCreated}, filas con error: ${result.rowErrors.size}"
                    )
                    result.rowErrors.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (result.rowErrors.size > 5) {
                        Text("... y ${result.rowErrors.size - 5} más", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissExcelResult) { Text("OK") } }
        )
    }
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Agregar a la lista activa") },
            text = {
                if (state.availableProducts.isEmpty()) {
                    Text("No hay otros productos disponibles. Crea uno nuevo.")
                } else {
                    LazyColumn {
                        items(state.availableProducts, key = { it.id }) { product ->
                            TextButton(onClick = {
                                viewModel.addProduct(product.id)
                                showAddDialog = false
                            }) { Text("${product.name} (${product.category})") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    onNewProduct()
                }) { Text("Producto nuevo") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cerrar") } }
        )
    }
}
