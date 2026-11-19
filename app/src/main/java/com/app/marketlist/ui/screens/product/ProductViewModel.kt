package com.app.marketlist.ui.screens.product

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.data.local.photo.PhotoFileProvider
import com.app.marketlist.data.local.photo.PhotoManager
import com.app.marketlist.data.remote.firebase.SyncScheduler
import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.ProductRepository
import com.app.marketlist.domain.repository.StoreRepository
import com.app.marketlist.domain.usecase.AppErrorException
import com.app.marketlist.domain.usecase.BarcodeValidator
import com.app.marketlist.domain.usecase.CreateProductUseCase
import com.app.marketlist.domain.usecase.UpdateProductUseCase
import com.app.marketlist.ui.components.toUserMessage
import com.app.marketlist.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val FIELD_NAME = "nombre"
const val FIELD_CATEGORY = "categoría"
const val FIELD_STORE = "tienda"
const val FIELD_BARCODE = "código de barras"

data class ProductFormState(
    val name: String = "",
    val category: String = "",
    val selectedStoreIds: Set<Long> = emptySet(),
    val barcode: String = "",
    val photoPath: String? = null,
    /** Texto del precio inicial por tienda (storeId -> texto). */
    val initialPrices: Map<Long, String> = emptyMap(),
    val priceErrors: Map<Long, String> = emptyMap(),
    val errors: Set<String> = emptySet(),
    val isEditing: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null
) {
    /** Validación pura de los campos obligatorios; devuelve los campos con error. */
    fun validate(): Set<String> = buildSet {
        if (name.trim().length < 2 || name.trim().length > 100) add(FIELD_NAME)
        if (category.isBlank()) add(FIELD_CATEGORY)
        if (selectedStoreIds.isEmpty()) add(FIELD_STORE)
        if (barcode.isNotBlank() && !BarcodeValidator.isValid(barcode.trim())) add(FIELD_BARCODE)
    }
}

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val productRepo: ProductRepository,
    private val monthlyListRepo: MonthlyListRepository,
    storeRepo: StoreRepository,
    private val createProduct: CreateProductUseCase,
    private val updateProduct: UpdateProductUseCase,
    private val activeListManager: ActiveListManager,
    private val priceRegistrar: PriceRegistrar,
    private val photoManager: PhotoManager,
    private val photoFileProvider: PhotoFileProvider,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val productId: Long = savedStateHandle.get<Long>(Screen.ProductForm.ARG_PRODUCT_ID) ?: -1L

    private val _state = MutableStateFlow(
        ProductFormState(
            isEditing = productId >= 0,
            barcode = savedStateHandle.get<String>(Screen.ProductForm.ARG_BARCODE).orEmpty()
        )
    )
    val state: StateFlow<ProductFormState> = _state.asStateFlow()

    val stores: StateFlow<List<Store>> = storeRepo.getAllStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (productId >= 0) {
            viewModelScope.launch {
                // searchProducts("") devuelve todos los productos de la base local.
                val product = productRepo.searchProducts("", 0).first().firstOrNull { it.id == productId }
                if (product != null) {
                    _state.value = _state.value.copy(
                        name = product.name,
                        category = product.category,
                        selectedStoreIds = product.stores.map { it.id }.toSet(),
                        barcode = product.barcode.orEmpty(),
                        photoPath = product.photoPath,
                        initialPrices = product.initialPrices.mapValues { formatPrice(it.value) }
                    )
                }
            }
        }
        // Resultado del escáner (se entrega via SavedStateHandle de esta entrada).
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(Screen.ProductForm.RESULT_SCANNED_BARCODE, null).collect { code ->
                if (code != null) {
                    onBarcodeChange(code)
                    savedStateHandle[Screen.ProductForm.RESULT_SCANNED_BARCODE] = null
                }
            }
        }
    }

    fun onNameChange(value: String) = update { copy(name = value.take(100), errors = errors - FIELD_NAME) }
    fun onCategoryChange(value: String) = update { copy(category = value, errors = errors - FIELD_CATEGORY) }
    fun onBarcodeChange(value: String) = update { copy(barcode = value.trim(), errors = errors - FIELD_BARCODE) }

    fun onStoreToggle(storeId: Long) = update {
        val next = if (storeId in selectedStoreIds) selectedStoreIds - storeId else selectedStoreIds + storeId
        copy(selectedStoreIds = next, errors = errors - FIELD_STORE)
    }

    fun onInitialPriceChange(storeId: Long, value: String) = update {
        copy(initialPrices = initialPrices + (storeId to value), priceErrors = priceErrors - storeId)
    }
    fun consumeMessage() = update { copy(message = null) }

    fun createPhotoUri(): Uri = photoFileProvider.createTempPhotoUri()

    fun onPhotoCaptured(uri: Uri) {
        viewModelScope.launch {
            val ownerId = if (productId >= 0) productId else 0L
            photoManager.savePhotoFromUri(uri, ownerId)
                .onSuccess { path -> update { copy(photoPath = path) } }
                .onFailure { update { copy(message = "No se pudo guardar la foto") } }
        }
    }

    fun photoFile(): java.io.File? = _state.value.photoPath?.let { photoManager.getPhotoFile(it) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val errors = current.validate()
        val parsedPrices = mutableMapOf<Long, Double>()
        val priceErrors = mutableMapOf<Long, String>()
        current.selectedStoreIds.forEach { storeId ->
            val text = current.initialPrices[storeId].orEmpty()
            if (text.isNotBlank()) {
                when (val parsed = PriceInputValidator.parse(text)) {
                    is PriceParseResult.Valid -> parsedPrices[storeId] = parsed.value
                    is PriceParseResult.Invalid -> priceErrors[storeId] = parsed.message
                }
            }
        }
        if (errors.isNotEmpty() || priceErrors.isNotEmpty()) {
            _state.value = current.copy(errors = errors, priceErrors = priceErrors)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, errors = emptySet())
            val selectedStores = stores.value.filter { it.id in current.selectedStoreIds }
            val barcode = current.barcode.trim().ifBlank { null }

            if (barcode != null) {
                val existing = productRepo.getProductByBarcode(barcode).first()
                if (existing != null && existing.id != productId) {
                    _state.value = _state.value.copy(
                        saving = false,
                        errors = setOf(FIELD_BARCODE),
                        message = "Ya existe un producto con ese código de barras (${existing.name})"
                    )
                    return@launch
                }
            }

            val product = Product(
                id = if (current.isEditing) productId else 0,
                name = current.name.trim(),
                category = current.category.trim(),
                barcode = barcode,
                photoPath = current.photoPath,
                stores = selectedStores,
                initialPrices = parsedPrices
            )

            val result = if (current.isEditing) {
                updateProduct(product)
            } else {
                createProduct(product).mapCatching { newId ->
                    val activeList = activeListManager.ensureActive()
                    if (activeList != null) {
                        monthlyListRepo.addProductToList(newId, activeList.id).getOrThrow()
                        parsedPrices.forEach { (storeId, price) ->
                            priceRegistrar.register(newId, storeId, activeList.id, price).getOrThrow()
                        }
                    }
                    Unit
                }
            }

            result.onSuccess {
                syncScheduler.requestSync()
                _state.value = _state.value.copy(saving = false, saved = true)
            }.onFailure { error ->
                val fields = (error as? AppErrorException)?.appError
                    ?.let { it as? AppError.ValidationError }?.fields?.toSet()
                _state.value = _state.value.copy(
                    saving = false,
                    errors = fields ?: emptySet(),
                    message = error.toUserMessage()
                )
            }
        }
    }

    private fun formatPrice(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)

    private fun update(block: ProductFormState.() -> ProductFormState) {
        _state.value = _state.value.block()
    }
}
