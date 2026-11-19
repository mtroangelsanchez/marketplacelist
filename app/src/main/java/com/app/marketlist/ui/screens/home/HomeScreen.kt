package com.app.marketlist.ui.screens.home

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.marketlist.ui.components.EmptyState
import com.app.marketlist.ui.components.ProductListItem

@Composable
fun HomeScreen(
    onProductClick: (Long) -> Unit,
    onAddProduct: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProduct) {
                Icon(Icons.Default.Add, contentDescription = "Agregar producto")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "Mi Tienda de Super",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
            )
            state.activeList?.label?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                )
            }

            if (state.activeList != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Solo lista activa (${state.activeList?.label})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.onlyActiveList,
                        onCheckedChange = viewModel::onOnlyActiveListChange
                    )
                }
            }
            SyncStatusRow(
                lastSyncText = state.lastSyncText,
                syncError = state.syncError,
                onSyncNow = viewModel::syncNow
            )

            if (state.storageLimitReached) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Se alcanzó el límite de almacenamiento gratuito de Firebase. " +
                            "Los datos siguen guardados en el dispositivo.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Buscar producto") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = viewModel::onClearQuery) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )

            when {
                state.noMatches -> EmptyState(message = "No hay coincidencias") {
                    Button(onClick = onAddProduct) { Text("Agregar producto") }
                }

                state.allProducts.isEmpty() && state.onlyActiveList -> EmptyState(
                    message = "La lista activa no tiene productos. Desactiva \"Solo lista activa\" para ver todos los productos registrados."
                )

                state.allProducts.isEmpty() -> EmptyState(
                    message = "Aún no hay productos registrados. Usa el botón + para agregar uno."
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.visibleProducts, key = { it.id }) { product ->
                        ProductListItem(
                            product = product,
                            lowestPrice = state.lowestPriceByProduct[product.id],
                            onClick = { onProductClick(product.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusRow(lastSyncText: String?, syncError: Boolean, onSyncNow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (lastSyncText != null) "Última sincronización: $lastSyncText" else "Sin sincronizar",
                style = MaterialTheme.typography.bodySmall
            )
            if (syncError) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        " Error de sincronización. Se reintentará al haber conexión",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        IconButton(onClick = onSyncNow) {
            Icon(Icons.Default.Refresh, contentDescription = "Sincronizar ahora")
        }
    }
}
