package com.app.marketlist.ui.screens.monthlylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.marketlist.ui.components.EmptyState
import com.app.marketlist.ui.components.PriceBadge
import com.app.marketlist.ui.components.ScreenHeader

@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
    onProductClick: (Long) -> Unit,
    viewModel: ListDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = state.listLabel, onBack = onBack)
        state.storeName?.let {
            Text(
                "Precios en $it",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (state.items.isEmpty()) {
            EmptyState(message = "Esta lista no tiene productos")
        } else {
            LazyColumn {
                items(state.items, key = { it.product.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clickable { onProductClick(item.product.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.product.name, style = MaterialTheme.typography.titleMedium)
                            Text(item.product.category, style = MaterialTheme.typography.bodySmall)
                            if (item.prices.isEmpty()) {
                                Text("Sin precio registrado", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                item.prices.forEach { line ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(line.storeName, style = MaterialTheme.typography.bodyMedium)
                                        PriceBadge(price = line.price, isLowest = line.isLowest)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
