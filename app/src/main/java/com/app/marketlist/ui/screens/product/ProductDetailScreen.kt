package com.app.marketlist.ui.screens.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.marketlist.ui.components.PhotoOrPlaceholder
import com.app.marketlist.ui.components.PriceEntry
import com.app.marketlist.ui.components.PriceHistoryList
import com.app.marketlist.ui.components.ScreenHeader
import com.app.marketlist.ui.components.formatPrice

@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val product = state.product
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = product?.name ?: "Producto", onBack = onBack)

        if (product == null) {
            Text("Producto no encontrado", modifier = Modifier.padding(16.dp))
        } else Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PhotoOrPlaceholder(file = state.photoFile, modifier = Modifier.size(96.dp))
                Column {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text("Categoría: ${product.category}")
                    Text("Código de barras: ${product.barcode ?: "—"}")
                    Text("Tiendas: ${product.stores.joinToString(", ") { it.name }}")
                }
            }

            Row {
                IconButton(onClick = { onEdit(product.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar producto")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar producto")
                }
            }

            Text("Registrar precio", style = MaterialTheme.typography.titleSmall)
            PriceEntry(
                stores = state.availableStores,
                state = state.price,
                onStoreSelected = viewModel::onStoreSelected,
                onTextChange = viewModel::onPriceTextChange,
                onSubmit = viewModel::submitPrice
            )
            state.message?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.primary)
                LaunchedEffect(message) { viewModel.consumeMessage() }
            }

            Text("Historial de precios (últimas 6 listas)", style = MaterialTheme.typography.titleSmall)
            PriceHistoryList(groups = state.history)
            OutlinedButton(onClick = { onOpenHistory(product.id) }) { Text("Ver historial completo") }
        }
    }

    if (showDeleteDialog && product != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar producto") },
            text = {
                Text("¿Eliminar \"${product.name}\"? También se eliminarán todos sus precios registrados.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    state.pendingReplace?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReplace,
            title = { Text("Reemplazar precio") },
            text = {
                Text(
                    "Ya existe un precio de ${formatPrice(pending.currentPrice)} en ${pending.storeName} " +
                        "para esta lista. ¿Reemplazarlo por ${formatPrice(pending.newPrice)}?"
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmReplace) { Text("Reemplazar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissReplace) { Text("Cancelar") } }
        )
    }
}
