package com.app.marketlist.ui.screens.pricehistory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.ProductRepository
import com.app.marketlist.domain.repository.StoreRepository
import com.app.marketlist.domain.usecase.GetPriceComparisonUseCase
import com.app.marketlist.ui.components.PriceHistoryList
import com.app.marketlist.ui.components.ScreenHeader
import com.app.marketlist.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PriceHistoryUiState(val productName: String = "", val groups: List<PriceGroup> = emptyList())

@HiltViewModel
class PriceHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    productRepo: ProductRepository,
    monthlyListRepo: MonthlyListRepository,
    storeRepo: StoreRepository,
    getPriceComparison: GetPriceComparisonUseCase
) : ViewModel() {
    private val productId = savedStateHandle.get<Long>(Screen.PriceHistory.ARG_PRODUCT_ID) ?: -1L

    val uiState: StateFlow<PriceHistoryUiState> = combine(
        productRepo.searchProducts("", 0).map { list -> list.firstOrNull { it.id == productId } },
        getPriceComparison(productId),
        monthlyListRepo.getMonthlyLists(),
        storeRepo.getAllStores()
    ) { product, records, lists, stores ->
        PriceHistoryUiState(
            productName = product?.name ?: "Producto",
            groups = PriceHistoryBuilder.build(records, lists, stores)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceHistoryUiState())
}

@Composable
fun PriceHistoryScreen(onBack: () -> Unit, viewModel: PriceHistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Historial: ${state.productName}", onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            PriceHistoryList(groups = state.groups)
        }
    }
}
