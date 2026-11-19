package com.app.marketlist.ui.screens.store

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.marketlist.domain.model.StoreType
import com.app.marketlist.ui.components.ScreenHeader

private fun StoreType.label() = when (this) {
    StoreType.SUPERMARKET -> "Supermercado"
    StoreType.GENERAL_MARKET -> "Mercado general"
}

@Composable
fun StoreListScreen(onStoreClick: (Long) -> Unit, viewModel: StoreViewModel = hiltViewModel()) {
    val stores by viewModel.stores.collectAsState()
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startCreate) {
                Icon(Icons.Default.Add, contentDescription = "Agregar tienda")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(title = "Tiendas")
            state.message?.let { message ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(message, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::consumeMessage) { Text("OK") }
                    }
                }
            }
            LazyColumn {
                items(stores, key = { it.id }) { store ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clickable { onStoreClick(store.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(store.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    store.type.label() + if (store.isPredefined) " · Predefinida" else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (store.isPredefined) {
                                IconButton(onClick = { viewModel.askDelete(store) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "No se puede eliminar (predefinida)")
                                }
                            } else {
                                IconButton(onClick = { viewModel.startEdit(store) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar tienda")
                                }
                                IconButton(onClick = { viewModel.askDelete(store) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar tienda")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.form?.let { form ->
        AlertDialog(
            onDismissRequest = viewModel::dismissForm,
            title = { Text(if (form.editingId == null) "Nueva tienda" else "Editar tienda") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Nombre (2-50 caracteres)") },
                        singleLine = true,
                        isError = form.error != null,
                        supportingText = { form.error?.let { Text(it) } }
                    )
                    StoreType.values().forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = form.type == type, onClick = { viewModel.onTypeChange(type) })
                            Text(type.label())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::save) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissForm) { Text("Cancelar") } }
        )
    }

    state.storeToDelete?.let { store ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Eliminar tienda") },
            text = { Text("¿Eliminar \"${store.name}\"? También se eliminarán los precios asociados a esta tienda.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Cancelar") } }
        )
    }
}
