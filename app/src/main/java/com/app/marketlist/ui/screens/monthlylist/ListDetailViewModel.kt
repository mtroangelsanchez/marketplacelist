package com.app.marketlist.ui.screens.monthlylist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.PriceRepository
import com.app.marketlist.domain.repository.ProductRepository
import com.app.marketlist.domain.repository.StoreRepository
import com.app.marketlist.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProductPriceLine(val storeName: String, val price: Double, val isLowest: Boolean)

data class ListDetailItem(val product: Product, val prices: List<ProductPriceLine>)

data class ListDetailUiState(
    val listLabel: String = "",
    val storeName: String? = null,
    val items: List<ListDetailItem> = emptyList()
)

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    monthlyListRepo: MonthlyListRepository,
    productRepo: ProductRepository,
    priceRepo: PriceRepository,
    storeRepo: StoreRepository
) : ViewModel() {
    private val listId: Long = savedStateHandle.get<Long>(Screen.ListDetail.ARG_LIST_ID) ?: -1L
    private val storeId: Long = savedStateHandle.get<Long>(Screen.ListDetail.ARG_STORE_ID) ?: -1L

    val uiState: StateFlow<ListDetailUiState> = combine(
        monthlyListRepo.getMonthlyLists(),
        productRepo.getProducts(listId),
        priceRepo.getPricesForList(listId),
        storeRepo.getAllStores()
    ) { lists, products, prices, stores ->
        val storeNames = stores.associate { it.id to it.name }
        val items = products.map { product ->
            val productPrices = prices.filter { it.productId == product.id }
            val shown = if (storeId >= 0) productPrices.filter { it.storeId == storeId } else productPrices
            val lowest = productPrices.minOfOrNull { it.price }
            ListDetailItem(
                product = product,
                prices = shown.map {
                    ProductPriceLine(
                        storeName = it.storeName ?: storeNames[it.storeId] ?: "Tienda",
                        price = it.price,
                        isLowest = it.price == lowest
                    )
                }
            )
        }
        ListDetailUiState(
            listLabel = lists.firstOrNull { it.id == listId }?.label ?: "Lista",
            storeName = if (storeId >= 0) storeNames[storeId] else null,
            items = items
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState())
}
