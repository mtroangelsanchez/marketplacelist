package com.app.marketlist.ui.screens.store

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.StoreRepository
import com.app.marketlist.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StoreListsUiState(
    val storeName: String = "",
    val lists: List<MonthlyList> = emptyList()
)

@HiltViewModel
class StoreListsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    storeRepo: StoreRepository,
    monthlyListRepo: MonthlyListRepository
) : ViewModel() {
    private val storeId: Long = savedStateHandle.get<Long>(Screen.StoreLists.ARG_STORE_ID) ?: -1L

    val uiState: StateFlow<StoreListsUiState> = combine(
        storeRepo.getAllStores(),
        monthlyListRepo.getListsForStore(storeId)
    ) { stores, lists ->
        StoreListsUiState(
            storeName = stores.firstOrNull { it.id == storeId }?.name ?: "Tienda",
            lists = lists
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreListsUiState())
}
