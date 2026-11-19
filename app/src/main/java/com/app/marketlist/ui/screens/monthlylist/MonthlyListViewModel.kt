package com.app.marketlist.ui.screens.monthlylist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.data.remote.firebase.SyncScheduler
import com.app.marketlist.domain.model.ExcelImportResult
import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.ProductRepository
import com.app.marketlist.domain.usecase.CopyMonthlyListUseCase
import com.app.marketlist.domain.usecase.CreateMonthlyListUseCase
import com.app.marketlist.domain.usecase.ImportProductsFromExcelUseCase
import com.app.marketlist.domain.usecase.ListNameSuggester
import com.app.marketlist.ui.components.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class NewListForm(val name: String, val month: Int, val year: Int)

data class MonthlyListUiState(
    val lists: List<MonthlyList> = emptyList(),
    val activeList: MonthlyList? = null,
    val activeProducts: List<Product> = emptyList(),
    val availableProducts: List<Product> = emptyList(),
    val newListForm: NewListForm? = null,
    val excelResult: ExcelImportResult? = null,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthlyListViewModel @Inject constructor(
    private val monthlyListRepo: MonthlyListRepository,
    private val productRepo: ProductRepository,
    private val createMonthlyList: CreateMonthlyListUseCase,
    private val copyMonthlyList: CopyMonthlyListUseCase,
    private val importProductsFromExcel: ImportProductsFromExcelUseCase,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val newListForm = MutableStateFlow<NewListForm?>(null)
    private val excelResult = MutableStateFlow<ExcelImportResult?>(null)

    private val activeProducts = monthlyListRepo.getActiveMonthlyList().flatMapLatest { list ->
        if (list == null) flowOf(emptyList()) else productRepo.getProducts(list.id)
    }

    // searchProducts("") devuelve todos los productos de la base local.
    private val allProducts = productRepo.searchProducts("", 0)

    val uiState: StateFlow<MonthlyListUiState> = combine(
        monthlyListRepo.getMonthlyLists(),
        monthlyListRepo.getActiveMonthlyList(),
        activeProducts,
        allProducts,
        combine(message, newListForm, excelResult) { msg, form, excel -> Triple(msg, form, excel) }
    ) { lists, active, inList, all, overlay ->
        val (msg, form, excel) = overlay
        val inListIds = inList.map { it.id }.toSet()
        MonthlyListUiState(
            lists = lists,
            activeList = active,
            activeProducts = inList,
            availableProducts = all.filter { it.id !in inListIds },
            newListForm = form,
            excelResult = excel,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlyListUiState())

    fun startCreate() {
        viewModelScope.launch {
            val lists = monthlyListRepo.getMonthlyLists().first()
            val suggestion = ListNameSuggester.suggestNext(lists, LocalDate.now())
            newListForm.value = NewListForm(suggestion.label, suggestion.month, suggestion.year)
        }
    }

    fun onNewListNameChange(value: String) {
        newListForm.value = newListForm.value?.copy(name = value.take(50))
    }

    fun dismissCreate() {
        newListForm.value = null
    }

    fun confirmCreate() {
        val form = newListForm.value ?: return
        newListForm.value = null
        viewModelScope.launch {
            val previous = monthlyListRepo.getMonthlyLists().first().firstOrNull()
            createMonthlyList(form.month, form.year, form.name)
                .onSuccess { created ->
                    if (previous != null) {
                        copyMonthlyList(previous.id, created.id)
                            .onFailure { message.value = it.toUserMessage() }
                    }
                    syncScheduler.requestSync()
                    message.value = if (previous != null) {
                        "Lista ${created.label} creada con los productos de ${previous.label}"
                    } else {
                        "Lista ${created.label} creada"
                    }
                }
                .onFailure { message.value = it.toUserMessage() }
        }
    }

    fun importExcel(uri: Uri) {
        viewModelScope.launch {
            importProductsFromExcel(uri)
                .onSuccess {
                    excelResult.value = it
                    syncScheduler.requestSync()
                }
                .onFailure { message.value = it.toUserMessage() }
        }
    }

    fun dismissExcelResult() {
        excelResult.value = null
    }

    fun addProduct(productId: Long) {
        val list = uiState.value.activeList ?: return
        viewModelScope.launch {
            monthlyListRepo.addProductToList(productId, list.id)
                .onSuccess { syncScheduler.requestSync() }
                .onFailure { message.value = it.toUserMessage() }
        }
    }

    fun removeProduct(productId: Long) {
        val list = uiState.value.activeList ?: return
        viewModelScope.launch {
            monthlyListRepo.removeProductFromList(productId, list.id)
                .onSuccess { syncScheduler.requestSync() }
                .onFailure { message.value = it.toUserMessage() }
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
