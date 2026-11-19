package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.repository.StoreRepository
import javax.inject.Inject

class DeleteStoreUseCase @Inject constructor(
    private val storeRepo: StoreRepository
) {
    suspend operator fun invoke(storeId: Long): Result<Unit> {
        if (storeRepo.isStorePredefined(storeId)) {
            return Result.failure(
                AppErrorException(
                    AppError.PredefinedEntityError("Las tiendas predefinidas no pueden eliminarse")
                )
            )
        }

        return storeRepo.deleteStore(storeId)
    }
}
