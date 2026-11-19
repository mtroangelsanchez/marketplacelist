package com.app.marketlist.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.ui.screens.pricehistory.PriceGroup
import com.app.marketlist.ui.screens.product.PriceFormState
import java.io.File
import java.util.Locale

fun formatPrice(price: Double): String = String.format(Locale.US, "$%.2f", price)

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
            }
        } else {
            Spacer(Modifier.size(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        action?.invoke()
    }
}

/** Precio con color diferenciado cuando es el más bajo. */
@Composable
fun PriceBadge(price: Double, isLowest: Boolean) {
    val color = if (isLowest) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            formatPrice(price),
            color = color,
            fontWeight = if (isLowest) FontWeight.Bold else FontWeight.Normal
        )
        if (isLowest) {
            Text("Más bajo", color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ProductListItem(
    product: Product,
    lowestPrice: Double?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(product.category, style = MaterialTheme.typography.bodySmall)
            }
            if (lowestPrice != null) {
                PriceBadge(price = lowestPrice, isLowest = true)
            }
        }
    }
}

/** Muestra la foto del archivo o un placeholder si no existe. */
@Composable
fun PhotoOrPlaceholder(file: File?, modifier: Modifier = Modifier) {
    val bitmap = remember(file?.absolutePath) {
        file?.let {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(it.absolutePath, options)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Foto del producto",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Sin foto", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Componente de ingreso de precio: selector de tienda, campo decimal y botón de registro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceEntry(
    stores: List<Store>,
    state: PriceFormState,
    onStoreSelected: (Long) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = stores.firstOrNull { it.id == state.storeId }?.name ?: ""

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Tienda") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                stores.forEach { store ->
                    DropdownMenuItem(
                        text = { Text(store.name) },
                        onClick = {
                            onStoreSelected(store.id)
                            expanded = false
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChange,
            label = { Text("Precio") },
            isError = state.error != null,
            supportingText = { state.error?.let { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        if (onSubmit != null) {
            Button(onClick = onSubmit, enabled = state.storeId != null) {
                Text("Registrar precio")
            }
        }
    }
}

@Composable
fun PriceHistoryList(groups: List<PriceGroup>, modifier: Modifier = Modifier) {
    if (groups.isEmpty()) {
        Text("Sin historial de precios", style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { group ->
            Text(group.listLabel, style = MaterialTheme.typography.titleSmall)
            group.rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${row.storeName} · ${row.date}", style = MaterialTheme.typography.bodyMedium)
                    PriceBadge(price = row.price, isLowest = row.isLowest)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
