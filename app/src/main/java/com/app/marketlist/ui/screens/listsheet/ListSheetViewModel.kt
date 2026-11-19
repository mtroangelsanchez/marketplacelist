package com.app.marketlist.ui.screens.listsheet

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.data.local.photo.PhotoFileProvider
import com.app.marketlist.data.ocr.TicketTextRecognizer
import com.app.marketlist.data.remote.firebase.SyncScheduler
import com.app.marketlist.domain.model.ListSheetImportRequest
import com.app.marketlist.domain.model.ListSheetLine
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.PriceRepository
import com.app.marketlist.domain.repository.ProductRepository
import com.app.marketlist.domain.repository.StoreRepository
import com.app.marketlist.domain.usecase.ImportListSheetUseCase
import com.app.marketlist.domain.usecase.ListNameSuggester
import com.app.marketlist.domain.usecase.ProductMatcher
import com.app.marketlist.domain.usecase.SheetListParser
import com.app.marketlist.ui.components.toUserMessage
import com.app.marketlist.ui.screens.product.PriceInputValidator
import com.app.marketlist.ui.screens.product.PriceParseResult
import com.app.marketlist.ui.screens.ticket.ProductChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class ListSheetPhase { Pick, Processing, Review, Saving }

/** Último precio registrado de un producto; solo se sugiere, no se guarda hasta pulsar "Usar". */
data class SuggestedPriceUi(val price: Double, val storeId: Long, val storeName: String, val date: LocalDate) {
    fun text(): String =
        "Sugerido $${String.format(Locale.US, "%.2f", price)} · $storeName · " +
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

data class ListSheetLineUi(
    val id: Long,
    val include: Boolean = true,
    val name: String = "",
    val priceText: String = "",
    val priceError: String? = null,
    val choice: ProductChoice = ProductChoice.New,
    val suggested: Boolean = false,
    val suggestion: SuggestedPriceUi? = null,
    /** Tienda del precio cuando se pulsó "Usar"; null si el precio es escrito (usa la tienda de referencia). */
    val priceStoreId: Long? = null
)

data class ListSheetUiState(
    val phase: ListSheetPhase = ListSheetPhase.Pick,
    val lines: List<ListSheetLineUi> = emptyList(),
    val referenceStoreId: Long? = null,
    val rawText: String = "",
    val periodMonth: Int = 0,
    val periodYear: Int = 0,
    val label: String = "",
    val message: String? = null,
    val savedListId: Long? = null,
    val savedMessage: String? = null
)

@HiltViewModel
class ListSheetViewModel @Inject constructor(
    private val recognizer: TicketTextRecognizer,
    private val importSheet: ImportListSheetUseCase,
    private val storeRepo: StoreRepository,
    private val productRepo: ProductRepository,
    private val priceRepo: PriceRepository,
    private val monthlyListRepo: MonthlyListRepository,
    private val photoFileProvider: PhotoFileProvider,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(ListSheetUiState())
    val state: StateFlow<ListSheetUiState> = _state.asStateFlow()

    val stores: StateFlow<List<Store>> = storeRepo.getAllStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products: StateFlow<List<Product>> = productRepo.searchProducts("", 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var nextLineId = 1L

    private fun update(block: ListSheetUiState.() -> ListSheetUiState) {
        _state.value = _state.value.block()
    }

    fun createPhotoUri(): Uri = photoFileProvider.createTempPhotoUri()

    fun discardTemp(uri: Uri) = recognizer.deleteTemp(uri)

    fun onImageSelected(uri: Uri, isTemp: Boolean) {
        if (_state.value.phase == ListSheetPhase.Processing) return
        update { copy(phase = ListSheetPhase.Processing, message = null) }
        viewModelScope.launch {
            val result = recognizer.recognizeRows(uri)
            if (isTemp) recognizer.deleteTemp(uri)
            result
                .onSuccess { rows -> buildReview(rows) }
                .onFailure { update { copy(phase = ListSheetPhase.Pick, message = it.message ?: "No se pudo leer la lista") } }
        }
    }

    private suspend fun buildReview(rows: List<String>) {
        val catalog = productRepo.searchProducts("", 0).first()
        val lists = monthlyListRepo.getMonthlyLists().first()
        val today = LocalDate.now()
        val suggestion = ListNameSuggester.suggestNext(lists, today)
        val lines = SheetListParser.parse(rows).map { draft ->
            val match = ProductMatcher.bestMatch(draft.name, catalog)
            ListSheetLineUi(
                id = nextLineId++,
                name = draft.name,
                priceText = draft.price?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
                choice = if (match != null) ProductChoice.Existing(match) else ProductChoice.New,
                suggested = match != null,
                suggestion = match?.let { latestSuggestion(it.id) }
            )
        }
        update {
            copy(
                phase = ListSheetPhase.Review,
                lines = lines,
                rawText = rows.joinToString("\n"),
                periodMonth = suggestion.month,
                periodYear = suggestion.year,
                label = "Lista " + today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                message = if (lines.isEmpty()) "No se reconocieron artículos; agrega líneas manualmente" else null
            )
        }
    }

    private suspend fun latestSuggestion(productId: Long): SuggestedPriceUi? {
        val record = priceRepo.getLatestPrice(productId) ?: return null
        return SuggestedPriceUi(record.price, record.storeId, record.storeName ?: "Tienda", record.recordDate)
    }

    private fun updateLine(id: Long, block: ListSheetLineUi.() -> ListSheetLineUi) {
        update { copy(lines = lines.map { if (it.id == id) it.block() else it }) }
    }

    fun onNameChange(id: Long, value: String) = updateLine(id) { copy(name = value.take(100)) }

    fun onPriceChange(id: Long, value: String) =
        updateLine(id) { copy(priceText = value.take(10), priceError = null, priceStoreId = null) }

    fun onIncludeChange(id: Long, value: Boolean) = updateLine(id) { copy(include = value) }

    fun onChoice(id: Long, choice: ProductChoice) {
        updateLine(id) { copy(choice = choice, suggested = false, suggestion = null) }
        if (choice is ProductChoice.Existing) {
            viewModelScope.launch {
                val suggestion = latestSuggestion(choice.product.id)
                updateLine(id) { if (this.choice == choice) copy(suggestion = suggestion) else this }
            }
        }
    }

    /** Copia el precio sugerido a la línea y fija la tienda de ese registro. */
    fun useSuggestion(id: Long) = updateLine(id) {
        val s = suggestion ?: return@updateLine this
        copy(
            priceText = String.format(Locale.US, "%.2f", s.price),
            priceError = null,
            priceStoreId = s.storeId
        )
    }

    fun addManualLine() {
        update { copy(lines = lines + ListSheetLineUi(id = nextLineId++)) }
    }

    fun removeLine(id: Long) {
        update { copy(lines = lines.filter { it.id != id }) }
    }

    fun onReferenceStoreSelected(storeId: Long?) = update { copy(referenceStoreId = storeId) }

    fun onLabelChange(value: String) = update { copy(label = value.take(50)) }

    fun confirm() {
        val current = _state.value
        if (current.phase != ListSheetPhase.Review) return
        if (current.lines.none { it.include }) {
            update { copy(message = "Incluye al menos un artículo") }
            return
        }
        var hasError = false
        val checked = current.lines.map { line ->
            if (!line.include) {
                line
            } else {
                val priceInvalid = line.priceText.isNotBlank() &&
                    PriceInputValidator.parse(line.priceText) is PriceParseResult.Invalid
                val nameInvalid = line.choice is ProductChoice.New && line.name.trim().length !in 2..100
                if (priceInvalid || nameInvalid) hasError = true
                line.copy(priceError = if (priceInvalid) PriceInputValidator.ERROR_MESSAGE else null)
            }
        }
        if (hasError) {
            update {
                copy(
                    lines = checked,
                    message = "Corrige los precios y los nombres (2 a 100 caracteres) de los productos nuevos"
                )
            }
            return
        }
        val needsStore = checked.any {
            it.include && it.priceText.isNotBlank() && it.priceStoreId == null
        }
        if (needsStore && current.referenceStoreId == null) {
            update {
                copy(
                    lines = checked,
                    message = "Elige la tienda de referencia: hay precios escritos sin tienda"
                )
            }
            return
        }
        val lines = checked.filter { it.include }.map { line ->
            val price = if (line.priceText.isBlank()) {
                null
            } else {
                (PriceInputValidator.parse(line.priceText) as PriceParseResult.Valid).value
            }
            when (val choice = line.choice) {
                is ProductChoice.Existing -> ListSheetLine(choice.product.id, null, price, line.priceStoreId)
                ProductChoice.New -> ListSheetLine(null, line.name.trim(), price, line.priceStoreId)
            }
        }
        val request = ListSheetImportRequest(
            label = current.label,
            month = current.periodMonth,
            year = current.periodYear,
            referenceStoreId = current.referenceStoreId,
            lines = lines
        )
        update { copy(lines = checked, phase = ListSheetPhase.Saving, message = null) }
        viewModelScope.launch {
            importSheet(request)
                .onSuccess { result ->
                    syncScheduler.requestSync()
                    update {
                        copy(
                            phase = ListSheetPhase.Review,
                            savedListId = result.listId,
                            savedMessage = "Lista creada: ${lines.size} artículos, " +
                                "${result.pricesRegistered} precios, ${result.productsCreated} productos nuevos"
                        )
                    }
                }
                .onFailure { update { copy(phase = ListSheetPhase.Review, message = it.toUserMessage()) } }
        }
    }

    fun consumeMessage() = update { copy(message = null) }
}
