package com.app.marketlist.ui.screens.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.data.local.photo.PhotoManager
import com.app.marketlist.data.remote.firebase.SyncScheduler
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.ProductRepository
import com.app.marketlist.domain.repository.StoreRepository
import com.app.marketlist.domain.usecase.DeleteProductUseCase
import com.app.marketlist.domain.usecase.GetPriceComparisonUseCase
import com.app.marketlist.ui.components.toUserMessage
import com.app.marketlist.ui.navigation.Screen
import com.app.marketlist.ui.screens.pricehistory.PriceGroup
import com.app.marketlist.ui.screens.pricehistory.PriceHistoryBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProductDetailUiState(
    val product: Product? = null,
    val photoFile: File? = null,
    val history: List<PriceGroup> = emptyList(),
    val availableStores: List<Store> = emptyList(),
    val price: PriceFormState = PriceFormState(),
    val pendingReplace: PendingReplace? = null,
    val message: String? = null,
    val deleted: Boolean = false
)

private data class DetailForm(
    val price: PriceFormState = PriceFormState(),
    val pendingReplace: PendingReplace? = null,
    val message: String? = null,
    val deleted: Boolean = false
)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepo: ProductRepository,
    monthlyListRepo: MonthlyListRepository,
    storeRepo: StoreRepository,
    getPriceComparison: GetPriceComparisonUseCase,
    private val deleteProduct: DeleteProductUseCase,
    private val activeListManager: ActiveListManager,
    private val priceRegistrar: PriceRegistrar,
    private val photoManager: PhotoManager,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val productId: Long = savedStateHandle.get<Long>(Screen.ProductDetail.ARG_PRODUCT_ID) ?: -1L
    private val form = MutableStateFlow(DetailForm())

    private val productFlow = productRepo.searchProducts("", 0)
        .map { products -> products.firstOrNull { it.id == productId } }

    val uiState: StateFlow<ProductDetailUiState> = combine(
        productFlow,
        getPriceComparison(productId),
        monthlyListRepo.getMonthlyLists(),
        storeRepo.getAllStores(),
        form
    ) { product, history, lists, stores, f ->
        ProductDetailUiState(
            product = product,
            photoFile = product?.photoPath?.let { photoManager.getPhotoFile(it) },
            history = PriceHistoryBuilder.build(history, lists, stores),
            availableStores = product?.stores ?: emptyList(),
            price = f.price,
            pendingReplace = f.pendingReplace,
            message = f.message,
            deleted = f.deleted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductDetailUiState())

    fun onStoreSelected(storeId: Long) {
        form.value = form.value.copy(price = form.value.price.copy(storeId = storeId, error = null))
    }

    fun onPriceTextChange(text: String) {
        form.value = form.value.copy(price = form.value.price.copy(text = text, error = null))
    }

    fun submitPrice() {
        val product = uiState.value.product ?: return
        val current = form.value.price
        val storeId = current.storeId ?: return
        when (val parsed = PriceInputValidator.parse(current.text)) {
            is PriceParseResult.Invalid ->
                form.value = form.value.copy(price = current.copy(error = parsed.message))

            is PriceParseResult.Valid -> viewModelScope.launch {
                val list = activeListManager.ensureActive() ?: return@launch
                val existing = priceRegistrar.findExisting(product.id, storeId, list.id)
                if (existing != null) {
                    val storeName = product.stores.firstOrNull { it.id == storeId }?.name ?: "la tienda"
                    form.value = form.value.copy(
                        pendingReplace = PendingReplace(storeId, storeName, existing.price, parsed.value)
                    )
                } else {
                    register(product.id, storeId, list.id, parsed.value)
                }
            }
        }
    }

    fun confirmReplace() {
        val pending = form.value.pendingReplace ?: return
        form.value = form.value.copy(pendingReplace = null)
        viewModelScope.launch {
            val list = activeListManager.ensureActive() ?: return@launch
            register(productId, pending.storeId, list.id, pending.newPrice)
        }
    }

    fun dismissReplace() {
        form.value = form.value.copy(pendingReplace = null)
    }

    fun consumeMessage() {
        form.value = form.value.copy(message = null)
    }

    fun delete() {
        viewModelScope.launch {
            deleteProduct(productId)
                .onSuccess {
                    syncScheduler.requestSync()
                    form.value = form.value.copy(deleted = true)
                }
                .onFailure { form.value = form.value.copy(message = it.toUserMessage()) }
        }
    }

    private suspend fun register(productId: Long, storeId: Long, listId: Long, price: Double) {
        priceRegistrar.register(productId, storeId, listId, price)
            .onSuccess {
                form.value = form.value.copy(price = PriceFormState(storeId = storeId), message = "Precio registrado")
            }
            .onFailure {
                form.value = form.value.copy(message = it.toUserMessage())
            }
    }
}
