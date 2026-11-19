package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.SyncResult
import com.app.marketlist.domain.repository.SyncRepository
import javax.inject.Inject

class SyncDataUseCase @Inject constructor(
    private val syncRepo: SyncRepository
) {
    suspend operator fun invoke(): Result<SyncResult> {
        return syncRepo.syncToCloud()
    }
}
