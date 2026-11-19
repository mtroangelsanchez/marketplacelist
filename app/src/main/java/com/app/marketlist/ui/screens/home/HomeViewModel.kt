package com.app.marketlist.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.app.marketlist.data.remote.firebase.SyncDateFormatter
import com.app.marketlist.data.remote.firebase.SyncScheduler
import com.app.marketlist.data.remote.firebase.SyncStatusReader
import com.app.marketlist.data.repository.SyncRepositoryImpl
import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.PriceRepository
import com.app.marketlist.domain.repository.ProductRepository
import com.app.marketlist.ui.screens.product.ActiveListManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val activeList: MonthlyList? = null,
    /** true cuando el usuario pidió ver solo los productos de la lista activa. */
    val onlyActiveList: Boolean = false,
    val query: String = "",
    val allProducts: List<Product> = emptyList(),
    val visibleProducts: List<Product> = emptyList(),
    val lowestPriceByProduct: Map<Long, Double> = emptyMap(),
    val lastSyncText: String? = null,
    val syncError: Boolean = false,
    val storageLimitReached: Boolean = false
) {
    val noMatches: Boolean get() = query.isNotBlank() && visibleProducts.isEmpty()
}

private data class SyncInfo(
    val lastSyncText: String? = null,
    val syncError: Boolean = false,
    val storageLimitReached: Boolean = false
)

private data class ProductsAndPrices(
    val products: List<Product> = emptyList(),
    val lowest: Map<Long, Double> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val activeListManager: ActiveListManager,
    monthlyListRepo: MonthlyListRepository,
    private val productRepo: ProductRepository,
    private val priceRepo: PriceRepository,
    private val syncStatusReader: SyncStatusReader,
    private val syncScheduler: SyncScheduler,
    private val workManager: WorkManager
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val onlyActiveList = MutableStateFlow(false)
    private val syncInfo = MutableStateFlow(SyncInfo())
    private val formatter = SyncDateFormatter()

    private val activeList = monthlyListRepo.getActiveMonthlyList()

    private fun lowestByProduct(prices: List<PriceRecord>): Map<Long, Double> =
        prices.groupBy { it.productId }.mapValues { entry -> entry.value.minOf { it.price } }

    private fun listProducts(list: MonthlyList): Flow<ProductsAndPrices> =
        combine(productRepo.getProducts(list.id), priceRepo.getPricesForList(list.id)) { products, prices ->
            ProductsAndPrices(products = products, lowest = lowestByProduct(prices))
        }

    private fun allProducts(): Flow<ProductsAndPrices> =
        combine(productRepo.searchProducts("", 0), priceRepo.getAllPrices()) { products, prices ->
            ProductsAndPrices(products = products, lowest = lowestByProduct(prices))
        }

    // Por defecto muestra todos los productos registrados. Con el filtro "Solo lista activa"
    // muestra únicamente los de la lista activa (la del período más reciente).
    private val productsAndPrices = combine(activeList, onlyActiveList) { list, only -> list to only }
        .flatMapLatest { (list, only) ->
            if (only && list != null) listProducts(list) else allProducts()
        }

    val uiState: StateFlow<HomeUiState> = combine(
        activeList, productsAndPrices, query, syncInfo, onlyActiveList
    ) { list, data, q, sync, only ->
        HomeUiState(
            activeList = list,
            onlyActiveList = only,
            query = q,
            allProducts = data.products,
            visibleProducts = ProductFilter.apply(data.products, q),
            lowestPriceByProduct = data.lowest,
            lastSyncText = sync.lastSyncText,
            syncError = sync.syncError,
            storageLimitReached = sync.storageLimitReached
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            activeListManager.ensureActive()
            refreshSync(syncError = false)
        }
        viewModelScope.launch {
            try {
                workManager.getWorkInfosForUniqueWorkFlow(SyncRepositoryImpl.SYNC_WORK_NAME).collect { infos ->
                    val failed = infos.any {
                        it.state == WorkInfo.State.FAILED ||
                            (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0)
                    }
                    refreshSync(syncError = failed)
                }
            } catch (e: Exception) {
                // La sincronización es opcional: la app sigue funcionando offline.
            }
        }
    }

    private suspend fun refreshSync(syncError: Boolean) {
        val last = syncStatusReader.lastSyncTime()
        syncInfo.value = SyncInfo(
            lastSyncText = last?.let { formatter.format(it) },
            syncError = syncError,
            storageLimitReached = syncStatusReader.isStorageLimitReached()
        )
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onOnlyActiveListChange(value: Boolean) {
        onlyActiveList.value = value
    }

    fun onClearQuery() {
        query.value = ""
    }

    fun syncNow() {
        syncScheduler.requestSync()
    }
}
