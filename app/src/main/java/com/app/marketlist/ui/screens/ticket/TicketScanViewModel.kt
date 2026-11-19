package com.app.marketlist.ui.screens.ticket

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.data.local.photo.PhotoFileProvider
import com.app.marketlist.data.ocr.TicketTextRecognizer
import com.app.marketlist.data.remote.firebase.SyncScheduler
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.model.StoreType
import com.app.marketlist.domain.model.TicketImportLine
import com.app.marketlist.domain.model.TicketImportRequest
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.ProductRepository
import com.app.marketlist.domain.repository.StoreRepository
import com.app.marketlist.domain.usecase.CreateStoreUseCase
import com.app.marketlist.domain.usecase.ImportTicketUseCase
import com.app.marketlist.domain.usecase.ProductMatcher
import com.app.marketlist.domain.usecase.TicketParser
import com.app.marketlist.domain.usecase.TicketPeriodResolver
import com.app.marketlist.ui.components.toUserMessage
import com.app.marketlist.ui.screens.product.PriceInputValidator
import com.app.marketlist.ui.screens.product.PriceParseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class TicketPhase { Pick, Processing, Review, Saving }

sealed interface ProductChoice {
    /** Producto nuevo con el nombre de la descripción de la línea. */
    object New : ProductChoice
    data class Existing(val product: Product) : ProductChoice
}

data class TicketLineUi(
    val id: Long,
    val include: Boolean = true,
    val description: String = "",
    val priceText: String = "",
    val priceError: String? = null,
    val choice: ProductChoice = ProductChoice.New,
    val suggested: Boolean = false,
    /** Categoría que tendrá el producto si es nuevo (sugerida por el departamento del ticket). */
    val category: String = ""
)

data class TicketScanUiState(
    val phase: TicketPhase = TicketPhase.Pick,
    val lines: List<TicketLineUi> = emptyList(),
    val selectedStoreId: Long? = null,
    val detectedStoreName: String? = null,
    val rawText: String = "",
    val ticketDate: LocalDate = LocalDate.now(),
    val periodMonth: Int = 0,
    val periodYear: Int = 0,
    val label: String = "",
    val message: String? = null,
    val savedListId: Long? = null,
    val savedMessage: String? = null
)

@HiltViewModel
class TicketScanViewModel @Inject constructor(
    private val recognizer: TicketTextRecognizer,
    private val importTicket: ImportTicketUseCase,
    private val storeRepo: StoreRepository,
    private val productRepo: ProductRepository,
    private val monthlyListRepo: MonthlyListRepository,
    private val createStore: CreateStoreUseCase,
    private val photoFileProvider: PhotoFileProvider,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(TicketScanUiState())
    val state: StateFlow<TicketScanUiState> = _state.asStateFlow()

    val stores: StateFlow<List<Store>> = storeRepo.getAllStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products: StateFlow<List<Product>> = productRepo.searchProducts("", 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var nextLineId = 1L
    private var labelEdited = false
    private var copyFromListId: Long? = null

    private fun update(block: TicketScanUiState.() -> TicketScanUiState) {
        _state.value = _state.value.block()
    }

    fun createPhotoUri(): Uri = photoFileProvider.createTempPhotoUri()

    fun discardTemp(uri: Uri) = recognizer.deleteTemp(uri)

    fun onImageSelected(uri: Uri, isTemp: Boolean) {
        if (_state.value.phase == TicketPhase.Processing) return
        update { copy(phase = TicketPhase.Processing, message = null) }
        viewModelScope.launch {
            val result = recognizer.recognizeRows(uri)
            if (isTemp) recognizer.deleteTemp(uri)
            result
                .onSuccess { rows -> buildReview(rows) }
                .onFailure { update { copy(phase = TicketPhase.Pick, message = it.message ?: "No se pudo leer el ticket") } }
        }
    }

    private suspend fun buildReview(rows: List<String>) {
        val allStores = storeRepo.getAllStores().first()
        val catalog = productRepo.searchProducts("", 0).first()
        val lists = monthlyListRepo.getMonthlyLists().first()
        val parsed = TicketParser.parse(rows, allStores.map { it.name })
        val today = LocalDate.now()
        val date = parsed.date ?: today
        val period = TicketPeriodResolver.resolve(parsed.date, today, lists)
        copyFromListId = if (TicketPeriodResolver.needsCopyFromPrevious(period, lists)) {
            TicketPeriodResolver.mostRecent(lists)?.id
        } else {
            null
        }
        val detected = allStores.firstOrNull { it.name == parsed.storeName }
        labelEdited = false
        val lines = parsed.lines.map { draft ->
            val match = ProductMatcher.bestMatch(draft.description, catalog)
            TicketLineUi(
                id = nextLineId++,
                description = draft.description,
                priceText = draft.price?.let { formatInput(it) }.orEmpty(),
                choice = if (match != null) ProductChoice.Existing(match) else ProductChoice.New,
                suggested = match != null,
                category = draft.category.orEmpty()
            )
        }
        update {
            copy(
                phase = TicketPhase.Review,
                lines = lines,
                selectedStoreId = detected?.id,
                detectedStoreName = detected?.name,
                rawText = rows.joinToString("\n"),
                ticketDate = date,
                periodMonth = period.first,
                periodYear = period.second,
                label = defaultLabel(detected?.name, date),
                message = if (lines.isEmpty()) "No se reconocieron artículos; agrega líneas manualmente" else null
            )
        }
    }

    private fun formatInput(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    private fun defaultLabel(storeName: String?, date: LocalDate): String =
        TicketPeriodResolver.defaultLabel(storeName ?: "", date).replace("  ", " ")

    private fun updateLine(id: Long, block: TicketLineUi.() -> TicketLineUi) {
        update { copy(lines = lines.map { if (it.id == id) it.block() else it }) }
    }

    fun onDescriptionChange(id: Long, value: String) = updateLine(id) { copy(description = value.take(100)) }

    fun onCategoryChange(id: Long, value: String) = updateLine(id) { copy(category = value.take(50)) }
    fun onPriceChange(id: Long, value: String) = updateLine(id) { copy(priceText = value.take(10), priceError = null) }

    fun onIncludeChange(id: Long, value: Boolean) = updateLine(id) { copy(include = value) }

    fun onChoice(id: Long, choice: ProductChoice) = updateLine(id) { copy(choice = choice, suggested = false) }

    fun addManualLine() {
        update { copy(lines = lines + TicketLineUi(id = nextLineId++)) }
    }

    fun removeLine(id: Long) {
        update { copy(lines = lines.filter { it.id != id }) }
    }

    fun onStoreSelected(storeId: Long) {
        val name = stores.value.firstOrNull { it.id == storeId }?.name
        update {
            copy(
                selectedStoreId = storeId,
                label = if (labelEdited) label else defaultLabel(name, ticketDate)
            )
        }
    }

    fun createNewStore(name: String) {
        viewModelScope.launch {
            createStore(Store(name = name.trim(), type = StoreType.SUPERMARKET))
                .onSuccess { id -> onStoreSelectedAfterCreate(id, name.trim()) }
                .onFailure { update { copy(message = it.toUserMessage()) } }
        }
    }

    private fun onStoreSelectedAfterCreate(id: Long, name: String) {
        update {
            copy(
                selectedStoreId = id,
                label = if (labelEdited) label else defaultLabel(name, ticketDate)
            )
        }
    }

    fun onLabelChange(value: String) {
        labelEdited = true
        update { copy(label = value.take(50)) }
    }

    fun confirm() {
        val current = _state.value
        if (current.phase != TicketPhase.Review) return
        val storeId = current.selectedStoreId
        if (storeId == null) {
            update { copy(message = "Elige el supermercado del ticket") }
            return
        }
        val included = current.lines.filter { it.include }
        if (included.isEmpty()) {
            update { copy(message = "Incluye al menos un artículo") }
            return
        }
        var hasError = false
        val checked = current.lines.map { line ->
            if (!line.include) {
                line
            } else {
                val priceInvalid = PriceInputValidator.parse(line.priceText) is PriceParseResult.Invalid
                val nameInvalid = line.choice is ProductChoice.New && line.description.trim().length !in 2..100
                if (priceInvalid || nameInvalid) hasError = true
                line.copy(
                    priceError = if (priceInvalid) PriceInputValidator.ERROR_MESSAGE else null
                )
            }
        }
        val nameProblem = checked.any {
            it.include && it.choice is ProductChoice.New && it.description.trim().length !in 2..100
        }
        if (hasError) {
            update {
                copy(
                    lines = checked,
                    message = if (nameProblem) {
                        "Corrige los precios y los nombres (2 a 100 caracteres) de los productos nuevos"
                    } else {
                        "Corrige los precios marcados o quita esas líneas"
                    }
                )
            }
            return
        }
        val importLines = checked.filter { it.include }.map { line ->
            val price = (PriceInputValidator.parse(line.priceText) as PriceParseResult.Valid).value
            when (val choice = line.choice) {
                is ProductChoice.Existing -> TicketImportLine(choice.product.id, null, price)
                ProductChoice.New -> TicketImportLine(
                    null, line.description.trim(), price, line.category.trim().ifBlank { null }
                )
            }
        }
        val request = TicketImportRequest(
            storeId = storeId,
            label = current.label,
            month = current.periodMonth,
            year = current.periodYear,
            recordDate = current.ticketDate,
            copyFromListId = copyFromListId,
            lines = importLines
        )
        update { copy(lines = checked, phase = TicketPhase.Saving, message = null) }
        viewModelScope.launch {
            importTicket(request)
                .onSuccess { result ->
                    syncScheduler.requestSync()
                    val merged = if (result.duplicatesMerged > 0) {
                        "; ${result.duplicatesMerged} línea(s) repetida(s) se unieron"
                    } else {
                        ""
                    }
                    update {
                        copy(
                            phase = TicketPhase.Review,
                            savedListId = result.listId,
                            savedMessage = "Lista creada: ${result.pricesRegistered} precios, " +
                                "${result.productsCreated} productos nuevos$merged"
                        )
                    }
                }
                .onFailure { update { copy(phase = TicketPhase.Review, message = it.toUserMessage()) } }
        }
    }

    fun consumeMessage() = update { copy(message = null) }
}
