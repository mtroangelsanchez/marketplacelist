package com.app.marketlist.ui.components

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.usecase.AppErrorException

/** Convierte cualquier error en un mensaje legible para el usuario (en español). */
fun Throwable.toUserMessage(): String = when (this) {
    is AppErrorException -> appError.toUserMessage()
    else -> message ?: "Ocurrió un error inesperado"
}

fun AppError.toUserMessage(): String = when (this) {
    is AppError.ValidationError -> "Campos obligatorios o inválidos: ${fields.joinToString(", ")}"
    is AppError.DuplicateError -> "Ya existe $entity con el nombre \"$name\""
    is AppError.PredefinedEntityError -> message
    is AppError.DatabaseError -> "Error al acceder a la base de datos local"
    is AppError.StorageFullError -> "Almacenamiento lleno. Libera espacio e intenta de nuevo"
    is AppError.SyncError -> "No se pudo sincronizar. Se reintentará automáticamente"
    is AppError.CloudStorageLimitError -> message
    is AppError.CameraPermissionError -> message
    is AppError.BarcodeTimeoutError -> message
    is AppError.ExportPermissionError -> "Permisos insuficientes para exportar: $message"
    is AppError.ExportWriteError -> "Error al escribir el archivo de exportación"
    is AppError.ExportEmptyListError -> "La lista \"$listName\" no tiene precios registrados, no hay datos para exportar"
}
