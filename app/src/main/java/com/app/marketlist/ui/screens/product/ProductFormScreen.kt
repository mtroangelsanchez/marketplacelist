package com.app.marketlist.ui.screens.product

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.marketlist.ui.components.PhotoOrPlaceholder
import com.app.marketlist.ui.components.ScreenHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val stores by viewModel.stores.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) viewModel.onPhotoCaptured(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = viewModel.createPhotoUri()
            pendingPhotoUri = uri
            takePicture.launch(uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (state.isEditing) "Editar producto" else "Nuevo producto",
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nombre (2-100 caracteres)") },
                isError = FIELD_NAME in state.errors,
                supportingText = {
                    Text(if (FIELD_NAME in state.errors) "El nombre debe tener entre 2 y 100 caracteres" else "${state.name.length}/100")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            var categoryExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                OutlinedTextField(
                    value = state.category,
                    onValueChange = viewModel::onCategoryChange,
                    label = { Text("Categoría") },
                    isError = FIELD_CATEGORY in state.errors,
                    supportingText = { if (FIELD_CATEGORY in state.errors) Text("La categoría es obligatoria") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    singleLine = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    PRODUCT_CATEGORIES.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                viewModel.onCategoryChange(category)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Text("Tiendas (al menos una)", style = MaterialTheme.typography.titleSmall)
            if (FIELD_STORE in state.errors) {
                Text("Selecciona al menos una tienda", color = MaterialTheme.colorScheme.error)
            }
            stores.forEach { store ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = store.id in state.selectedStoreIds,
                        onCheckedChange = { viewModel.onStoreToggle(store.id) }
                    )
                    Text(store.name)
                }
            }

            OutlinedTextField(
                value = state.barcode,
                onValueChange = viewModel::onBarcodeChange,
                label = { Text("Código de barras (opcional)") },
                isError = FIELD_BARCODE in state.errors,
                supportingText = { if (FIELD_BARCODE in state.errors) Text("Debe tener entre 8 y 14 dígitos") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = onScan) { Text("Escanear código de barras") }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoOrPlaceholder(file = viewModel.photoFile(), modifier = Modifier.size(80.dp))
                OutlinedButton(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) {
                    Text("Tomar foto")
                }
            }

            Text("Precio inicial por tienda (opcional)", style = MaterialTheme.typography.titleSmall)
            val selectedStores = stores.filter { it.id in state.selectedStoreIds }
            if (selectedStores.isEmpty()) {
                Text("Selecciona tiendas para capturar su precio inicial", style = MaterialTheme.typography.bodySmall)
            }
            selectedStores.forEach { store ->
                val error = state.priceErrors[store.id]
                OutlinedTextField(
                    value = state.initialPrices[store.id].orEmpty(),
                    onValueChange = { viewModel.onInitialPriceChange(store.id, it) },
                    label = { Text("Precio en ${store.name}") },
                    isError = error != null,
                    supportingText = { if (error != null) Text(error) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state.message?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(if (state.saving) "Guardando..." else "Guardar")
            }
        }
    }
}
