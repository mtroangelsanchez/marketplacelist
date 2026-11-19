package com.app.marketlist.data.repository

import com.app.marketlist.data.local.db.StoreDao
import com.app.marketlist.data.local.db.toDomain
import com.app.marketlist.data.local.db.toEntity
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.repository.StoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepositoryImpl @Inject constructor(
    private val storeDao: StoreDao
) : StoreRepository {

    override fun getAllStores(): Flow<List<Store>> {
        return storeDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createStore(store: Store): Result<Long> {
        return try {
            val exists = storeDao.existsByNameAndType(store.name, store.type.name)
            if (exists) {
                Result.failure(
                    IllegalStateException("Ya existe una tienda con el nombre '${store.name}' y tipo '${store.type.name}'")
                )
            } else {
                val id = storeDao.insert(store.toEntity())
                Result.success(id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateStore(store: Store): Result<Unit> {
        return try {
            storeDao.update(store.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteStore(storeId: Long): Result<Unit> {
        return try {
            val store = storeDao.getById(storeId)
            if (store == null) {
                Result.failure(NoSuchElementException("Tienda no encontrada con id: $storeId"))
            } else if (store.isPredefined) {
                Result.failure(
                    IllegalStateException("Las tiendas predefinidas no pueden eliminarse")
                )
            } else {
                storeDao.deleteById(storeId)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isStorePredefined(storeId: Long): Boolean {
        // Note: This is a synchronous check - in practice the caller should ensure
        // the store data is already available. We use runBlocking as a fallback
        // since the interface defines this as a non-suspend function.
        return kotlinx.coroutines.runBlocking {
            storeDao.getById(storeId)?.isPredefined ?: false
        }
    }
}
