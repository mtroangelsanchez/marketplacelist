package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.repository.StoreRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CreateStoreUseCase @Inject constructor(
    private val storeRepo: StoreRepository
) {
    suspend operator fun invoke(store: Store): Result<Long> {
        val missingFields = mutableListOf<String>()

        if (store.name.length < 2 || store.name.length > 50) {
            missingFields.add("nombre")
        }

        if (missingFields.isNotEmpty()) {
            return Result.failure(
                AppErrorException(AppError.ValidationError(missingFields))
            )
        }

        // Check for duplicate name+type
        val existingStores = storeRepo.getAllStores().first()
        val duplicate = existingStores.any { existing ->
            existing.name.equals(store.name, ignoreCase = true) && existing.type == store.type
        }

        if (duplicate) {
            return Result.failure(
                AppErrorException(AppError.DuplicateError("tienda", store.name))
            )
        }

        return storeRepo.createStore(store)
    }
}
