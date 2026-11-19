package com.app.marketlist.ui.screens.store

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import com.app.marketlist.ui.components.ScreenHeader

@Composable
fun StoreListsScreen(
    onBack: () -> Unit,
    onListClick: (Long) -> Unit,
    viewModel: StoreListsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Listas de ${state.storeName}", onBack = onBack)
        if (state.lists.isEmpty()) {
            EmptyState(message = "Esta tienda aún no tiene listas")
        } else {
            LazyColumn {
                items(state.lists, key = { it.id }) { list ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clickable { onListClick(list.id) }
                    ) {
                        Text(
                            list.label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
