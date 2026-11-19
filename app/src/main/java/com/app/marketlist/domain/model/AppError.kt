package com.app.marketlist.domain.model

sealed class AppError {
    // Validación
    data class ValidationError(val fields: List<String>) : AppError()
    data class DuplicateError(val entity: String, val name: String) : AppError()
    data class PredefinedEntityError(val message: String) : AppError()

    // Almacenamiento
    data class DatabaseError(val cause: Throwable) : AppError()
    data class StorageFullError(val requiredSpace: Long) : AppError()

    // Sincronización
    data class SyncError(val cause: Throwable) : AppError()
    data class CloudStorageLimitError(val message: String) : AppError()

    // Cámara/Escáner
    data class CameraPermissionError(val message: String) : AppError()
    data class BarcodeTimeoutError(val message: String) : AppError()

    // Exportación
    data class ExportPermissionError(val message: String) : AppError()
    data class ExportWriteError(val cause: Throwable) : AppError()
    data class ExportEmptyListError(val listName: String) : AppError()
}
