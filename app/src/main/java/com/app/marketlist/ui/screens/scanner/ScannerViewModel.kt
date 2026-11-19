package com.app.marketlist.ui.screens.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.marketlist.data.camera.BarcodeTimeoutHandler
import com.app.marketlist.data.camera.CameraManager
import com.app.marketlist.domain.usecase.BarcodeValidator
import com.app.marketlist.domain.usecase.ScanBarcodeUseCase
import com.app.marketlist.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estados del escáner: escaneando, timeout, sin permiso (producto encontrado/nuevo se emiten como eventos). */
enum class ScannerStatus { SCANNING, TIMEOUT, NO_PERMISSION }

data class ScannerUiState(
    val status: ScannerStatus = ScannerStatus.SCANNING,
    val hasPermission: Boolean = false,
    val manualCode: String = "",
    val manualError: String? = null,
    val showManual: Boolean = false
)

sealed interface ScannerEvent {
    data class ProductFound(val productId: Long) : ScannerEvent
    data class NewProduct(val barcode: String) : ScannerEvent
    data class ReturnBarcode(val barcode: String) : ScannerEvent
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanBarcode: ScanBarcodeUseCase,
    val cameraManager: CameraManager
) : ViewModel() {

    private val forResult: Boolean = savedStateHandle.get<Boolean>(Screen.Scanner.ARG_FOR_RESULT) ?: false

    private val _state = MutableStateFlow(ScannerUiState(hasPermission = cameraManager.hasCameraPermission()))
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private val _events = Channel<ScannerEvent>(Channel.BUFFERED)
    val events: Flow<ScannerEvent> = _events.receiveAsFlow()

    private val timeoutHandler = BarcodeTimeoutHandler()
    private var timeoutJob: Job? = null
    private var handled = false

    init {
        if (_state.value.hasPermission) startScanning()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _state.value = _state.value.copy(hasPermission = true, status = ScannerStatus.SCANNING)
            startScanning()
        } else {
            _state.value = _state.value.copy(
                hasPermission = false,
                status = ScannerStatus.NO_PERMISSION,
                showManual = true
            )
        }
    }

    fun onCameraError() {
        _state.value = _state.value.copy(showManual = true)
    }

    fun startScanning() {
        handled = false
        timeoutJob?.cancel()
        _state.value = _state.value.copy(status = ScannerStatus.SCANNING)
        timeoutJob = viewModelScope.launch {
            timeoutHandler.startTimeout {
                if (!handled) _state.value = _state.value.copy(status = ScannerStatus.TIMEOUT, showManual = true)
            }
        }
    }

    fun toggleManual() {
        _state.value = _state.value.copy(showManual = !_state.value.showManual)
    }

    fun onManualCodeChange(value: String) {
        _state.value = _state.value.copy(manualCode = value.filter { it.isDigit() }.take(14), manualError = null)
    }

    fun submitManual() {
        val code = _state.value.manualCode
        if (!BarcodeValidator.isValid(code)) {
            _state.value = _state.value.copy(manualError = MANUAL_ERROR)
            return
        }
        onBarcodeDetected(code)
    }

    fun onBarcodeDetected(code: String) {
        if (handled) return
        handled = true
        timeoutJob?.cancel()
        viewModelScope.launch {
            if (forResult) {
                _events.send(ScannerEvent.ReturnBarcode(code))
            } else {
                val product = scanBarcode(code).first()
                _events.send(
                    if (product != null) ScannerEvent.ProductFound(product.id) else ScannerEvent.NewProduct(code)
                )
            }
        }
    }

    override fun onCleared() {
        timeoutHandler.cancel()
        super.onCleared()
    }

    companion object {
        const val MANUAL_ERROR = "El código debe tener entre 8 y 14 dígitos"
    }
}
