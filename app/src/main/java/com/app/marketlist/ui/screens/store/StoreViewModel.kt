package com.app.marketlist.ui.screens.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.data.remote.firebase.SyncScheduler
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.model.StoreType
import com.app.marketlist.domain.repository.StoreRepository
import com.app.marketlist.domain.usecase.CreateStoreUseCase
import com.app.marketlist.domain.usecase.DeleteStoreUseCase
import com.app.marketlist.ui.components.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado del diálogo de creación/edición de tienda. */
data class StoreFormState(
    val editingId: Long? = null,
    val name: String = "",
    val type: StoreType? = null,
    val error: String? = null
)

data class StoreScreenState(
    val form: StoreFormState? = null,
    val storeToDelete: Store? = null,
    val message: String? = null
)

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val storeRepo: StoreRepository,
    private val createStore: CreateStoreUseCase,
    private val deleteStore: DeleteStoreUseCase,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val stores: StateFlow<List<Store>> = storeRepo.getAllStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(StoreScreenState())
    val state: StateFlow<StoreScreenState> = _state.asStateFlow()

    fun startCreate() {
        _state.value = _state.value.copy(form = StoreFormState())
    }

    fun startEdit(store: Store) {
        if (store.isPredefined) {
            _state.value = _state.value.copy(message = "Las tiendas predefinidas no pueden editarse ni eliminarse")
            return
        }
        _state.value = _state.value.copy(form = StoreFormState(store.id, store.name, store.type))
    }

    fun onNameChange(value: String) = updateForm { copy(name = value.take(50), error = null) }
    fun onTypeChange(value: StoreType) = updateForm { copy(type = value, error = null) }
    fun dismissForm() { _state.value = _state.value.copy(form = null) }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun save() {
        val form = _state.value.form ?: return
        val name = form.name.trim()
        val type = form.type
        if (name.length < 2 || name.length > 50) {
            updateForm { copy(error = "El nombre debe tener entre 2 y 50 caracteres") }
            return
        }
        if (type == null) {
            updateForm { copy(error = "Selecciona el tipo de tienda") }
            return
        }
        viewModelScope.launch {
            val result: Result<Unit> = if (form.editingId == null) {
                createStore(Store(name = name, type = type)).map { }
            } else {
                val duplicate = storeRepo.getAllStores().first().any {
                    it.id != form.editingId && it.name.equals(name, ignoreCase = true) && it.type == type
                }
                if (duplicate) {
                    Result.failure<Unit>(IllegalStateException(DUPLICATE_MESSAGE))
                } else {
                    storeRepo.updateStore(Store(id = form.editingId, name = name, type = type))
                }
            }
            result.onSuccess {
                syncScheduler.requestSync()
                _state.value = _state.value.copy(form = null)
            }.onFailure { error ->
                val message = if (error.toUserMessage().contains("Ya existe", ignoreCase = true)) {
                    DUPLICATE_MESSAGE
                } else {
                    error.toUserMessage()
                }
                updateForm { copy(error = message) }
            }
        }
    }

    fun askDelete(store: Store) {
        if (store.isPredefined) {
            _state.value = _state.value.copy(message = "Las tiendas predefinidas no pueden eliminarse")
        } else {
            _state.value = _state.value.copy(storeToDelete = store)
        }
    }

    fun dismissDelete() { _state.value = _state.value.copy(storeToDelete = null) }

    fun confirmDelete() {
        val store = _state.value.storeToDelete ?: return
        _state.value = _state.value.copy(storeToDelete = null)
        viewModelScope.launch {
            deleteStore(store.id)
                .onSuccess { syncScheduler.requestSync() }
                .onFailure { _state.value = _state.value.copy(message = it.toUserMessage()) }
        }
    }

    private fun updateForm(block: StoreFormState.() -> StoreFormState) {
        val form = _state.value.form ?: return
        _state.value = _state.value.copy(form = form.block())
    }

    companion object {
        const val DUPLICATE_MESSAGE = "Ya existe una tienda con ese nombre y tipo"
    }
}
