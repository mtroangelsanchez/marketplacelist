package com.app.marketlist.ui.screens.export

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.usecase.ExportToExcelUseCase
import com.app.marketlist.domain.usecase.ListNameSuggester
import com.app.marketlist.ui.components.toUserMessage
import com.app.marketlist.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface ExportState {
    object Idle : ExportState
    object Exporting : ExportState
    data class Success(val uri: Uri) : ExportState
    data class Error(val message: String) : ExportState
}

data class ExportUiState(
    val listLabel: String = "",
    val month: Int = 0,
    val year: Int = 0,
    val state: ExportState = ExportState.Idle
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val monthlyListRepo: MonthlyListRepository,
    private val exportToExcel: ExportToExcelUseCase
) : ViewModel() {

    private val listId: Long = savedStateHandle.get<Long>(Screen.Export.ARG_LIST_ID) ?: -1L

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val list = monthlyListRepo.getMonthlyLists().first().firstOrNull { it.id == listId }
            _uiState.value = _uiState.value.copy(
                listLabel = list?.label ?: "",
                month = list?.month ?: 0,
                year = list?.year ?: 0
            )
        }
    }

    fun export() {
        if (_uiState.value.state is ExportState.Exporting) return
        _uiState.value = _uiState.value.copy(state = ExportState.Exporting)
        viewModelScope.launch {
            val result = withTimeoutOrNull(EXPORT_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { exportToExcel(listId) }
            }
            val newState: ExportState = if (result == null) {
                ExportState.Error("La exportación tardó más de 10 segundos. Intenta de nuevo")
            } else {
                result.fold(
                    onSuccess = { uri -> ExportState.Success(uri) },
                    onFailure = { error -> ExportState.Error(error.toUserMessage()) }
                )
            }
            _uiState.value = _uiState.value.copy(state = newState)
        }
    }

    fun suggestedFileName(): String {
        val s = _uiState.value
        return if (s.month in 1..12) {
            "Lista_${ListNameSuggester.monthName(s.month)}_${s.year}.xlsx"
        } else {
            "Lista.xlsx"
        }
    }

    fun reportError(message: String) {
        _uiState.value = _uiState.value.copy(state = ExportState.Error(message))
    }

    companion object {
        const val EXPORT_TIMEOUT_MS = 10_000L
    }
}
