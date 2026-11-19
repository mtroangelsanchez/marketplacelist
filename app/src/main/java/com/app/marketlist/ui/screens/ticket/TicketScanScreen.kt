package com.app.marketlist.ui.screens.ticket

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.usecase.ListNameSuggester
import com.app.marketlist.ui.components.ScreenHeader
import com.app.marketlist.ui.screens.product.PRODUCT_CATEGORIES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScanScreen(
    onBack: () -> Unit,
    onSaved: (listId: Long, storeId: Long?) -> Unit,
    viewModel: TicketScanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val stores by viewModel.stores.collectAsState()
    val products by viewModel.products.collectAsState()
    val context = LocalContext.current

    var pendingPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (uri != null) {
            if (success) viewModel.onImageSelected(uri, isTemp = true) else viewModel.discardTemp(uri)
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = viewModel.createPhotoUri()
            pendingPhotoUri = uri
            takePicture.launch(uri)
        } else {
            Toast.makeText(context, "Se necesita permiso de cámara para tomar la foto", Toast.LENGTH_SHORT).show()
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImageSelected(uri, isTemp = false)
    }

    LaunchedEffect(state.savedListId) {
        val listId = state.savedListId
        if (listId != null) {
            Toast.makeText(context, state.savedMessage.orEmpty(), Toast.LENGTH_LONG).show()
            onSaved(listId, state.selectedStoreId)
        }
    }

    var showNewStore by remember { mutableStateOf(false) }
    var newStoreName by remember { mutableStateOf("") }
    var pickingLineId by remember { mutableStateOf<Long?>(null) }
    var productFilter by remember { mutableStateOf("") }
    var storeMenu by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Leer ticket", onBack = onBack)

        if (state.phase == TicketPhase.Pick) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Toma una foto del ticket o elige una imagen. La lectura se hace en el teléfono " +
                        "y siempre podrás revisar y corregir los artículos antes de guardar.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tomar foto") }
                OutlinedButton(
                    onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Elegir de la galería") }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        } else if (state.phase == TicketPhase.Processing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Leyendo ticket…")
                }
            }
        } else {
            val saving = state.phase == TicketPhase.Saving
            val selectedStore = stores.firstOrNull { it.id == state.selectedStoreId }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    state.message?.let { message ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::consumeMessage) { Text("OK") }
                        }
                    }
                }
                item {
                    ExposedDropdownMenuBox(expanded = storeMenu, onExpandedChange = { storeMenu = it }) {
                        OutlinedTextField(
                            value = selectedStore?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Supermercado") },
                            placeholder = { Text("Elige el supermercado") },
                            supportingText = {
                                state.detectedStoreName?.let { Text("Detectado: $it") }
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = storeMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = storeMenu, onDismissRequest = { storeMenu = false }) {
                            stores.forEach { store ->
                                DropdownMenuItem(
                                    text = { Text(store.name) },
                                    onClick = {
                                        viewModel.onStoreSelected(store.id)
                                        storeMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Nueva tienda…") },
                                onClick = {
                                    storeMenu = false
                                    newStoreName = ""
                                    showNewStore = true
                                }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.label,
                        onValueChange = viewModel::onLabelChange,
                        label = { Text("Nombre de la lista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Período: ${ListNameSuggester.periodLabel(state.periodMonth.coerceIn(1, 12), state.periodYear)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                items(state.lines, key = { it.id }) { line ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = line.include, onCheckedChange = { viewModel.onIncludeChange(line.id, it) })
                                Text("Incluir", modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.removeLine(line.id) }) { Text("Quitar") }
                            }
                            OutlinedTextField(
                                value = line.description,
                                onValueChange = { viewModel.onDescriptionChange(line.id, it) },
                                label = { Text("Descripción") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = line.priceText,
                                onValueChange = { viewModel.onPriceChange(line.id, it) },
                                label = { Text("Precio") },
                                isError = line.priceError != null,
                                supportingText = { line.priceError?.let { Text(it) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                            val choice = line.choice
                            if (choice is ProductChoice.New) {
                                var categoryMenu by remember(line.id) { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = categoryMenu,
                                    onExpandedChange = { categoryMenu = it }
                                ) {
                                    OutlinedTextField(
                                        value = line.category,
                                        onValueChange = { viewModel.onCategoryChange(line.id, it) },
                                        label = { Text("Categoría") },
                                        placeholder = { Text("Otros") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenu) },
                                        singleLine = true,
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                        (listOf(line.category).filter { it.isNotBlank() && it !in PRODUCT_CATEGORIES } +
                                            PRODUCT_CATEGORIES).forEach { name ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    viewModel.onCategoryChange(line.id, name)
                                                    categoryMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            val choiceText = when (choice) {
                                is ProductChoice.Existing ->
                                    "Producto: ${choice.product.name}" + if (line.suggested) " (Sugerido)" else ""
                                ProductChoice.New -> "Producto nuevo: ${line.description.trim()}"
                            }
                            OutlinedButton(
                                onClick = {
                                    productFilter = ""
                                    pickingLineId = line.id
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(choiceText) }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = viewModel::addManualLine, modifier = Modifier.fillMaxWidth()) {
                        Text("Agregar línea")
                    }
                }
                item {
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "Ocultar texto reconocido" else "Ver texto reconocido")
                    }
                    if (showRaw) {
                        Text(state.rawText.ifBlank { "(sin texto)" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Button(
                        onClick = viewModel::confirm,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (saving) "Guardando…" else "Guardar lista") }
                }
            }
        }
    }

    if (showNewStore) {
        AlertDialog(
            onDismissRequest = { showNewStore = false },
            title = { Text("Nueva tienda") },
            text = {
                OutlinedTextField(
                    value = newStoreName,
                    onValueChange = { newStoreName = it.take(50) },
                    label = { Text("Nombre del supermercado") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createNewStore(newStoreName)
                    showNewStore = false
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showNewStore = false }) { Text("Cancelar") } }
        )
    }

    val pickingId = pickingLineId
    if (pickingId != null) {
        val filter = productFilter.trim()
        val shown: List<Product> = products.filter { filter.isEmpty() || it.name.contains(filter, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { pickingLineId = null },
            title = { Text("Asignar a producto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = productFilter,
                        onValueChange = { productFilter = it },
                        label = { Text("Buscar producto") },
                        singleLine = true
                    )
                    TextButton(onClick = {
                        viewModel.onChoice(pickingId, ProductChoice.New)
                        pickingLineId = null
                    }) { Text("Producto nuevo") }
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(shown, key = { it.id }) { product ->
                            TextButton(onClick = {
                                viewModel.onChoice(pickingId, ProductChoice.Existing(product))
                                pickingLineId = null
                            }) { Text(product.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingLineId = null }) { Text("Cerrar") } }
        )
    }
}
