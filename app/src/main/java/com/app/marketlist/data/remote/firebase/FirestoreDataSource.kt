package com.app.marketlist.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for Firebase Firestore operations.
 * Handles cloud synchronization using anonymous authentication
 * and organizes data under users/{userId}/ collections.
 *
 * Collection structure:
 *   users/{userId}/products
 *   users/{userId}/stores
 *   users/{userId}/monthlyLists
 *   users/{userId}/priceRecords
 */
@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_PRODUCTS = "products"
        private const val COLLECTION_STORES = "stores"
        private const val COLLECTION_MONTHLY_LISTS = "monthlyLists"
        private const val COLLECTION_PRICE_RECORDS = "priceRecords"
    }

    /**
     * Get current user ID from anonymous auth session.
     * Returns null if the user is not signed in.
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Sign in anonymously if not already signed in.
     * Returns the user ID on success.
     */
    suspend fun ensureAuthenticated(): Result<String> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Result.success(currentUser.uid)
            } else {
                val result = auth.signInAnonymously().await()
                val uid = result.user?.uid
                    ?: return Result.failure(IllegalStateException("Anonymous sign-in succeeded but user is null"))
                Result.success(uid)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a subcollection reference for the current authenticated user.
     * Throws IllegalStateException if user is not authenticated.
     */
    private fun getUserCollection(collection: String): CollectionReference {
        val userId = getCurrentUserId()
            ?: throw IllegalStateException("User is not authenticated. Call ensureAuthenticated() first.")
        return firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(collection)
    }

    // --- Batch Sync Operations ---

    /**
     * Sync products to Firestore using batch writes.
     * Each product map should contain a "id" key used as the document ID.
     */
    suspend fun syncProducts(products: List<Map<String, Any>>): Result<Unit> {
        return batchSync(COLLECTION_PRODUCTS, products)
    }

    /**
     * Sync stores to Firestore using batch writes.
     * Each store map should contain a "id" key used as the document ID.
     */
    suspend fun syncStores(stores: List<Map<String, Any>>): Result<Unit> {
        return batchSync(COLLECTION_STORES, stores)
    }

    /**
     * Sync monthly lists to Firestore using batch writes.
     * Each list map should contain a "id" key used as the document ID.
     */
    suspend fun syncMonthlyLists(lists: List<Map<String, Any>>): Result<Unit> {
        return batchSync(COLLECTION_MONTHLY_LISTS, lists)
    }

    /**
     * Sync price records to Firestore using batch writes.
     * Each record map should contain a "id" key used as the document ID.
     */
    suspend fun syncPriceRecords(records: List<Map<String, Any>>): Result<Unit> {
        return batchSync(COLLECTION_PRICE_RECORDS, records)
    }

    /**
     * Perform batch write for a collection.
     * Firestore limits batch writes to 500 operations, so we chunk accordingly.
     */
    private suspend fun batchSync(
        collectionName: String,
        items: List<Map<String, Any>>
    ): Result<Unit> {
        return try {
            val collection = getUserCollection(collectionName)

            // Firestore batch limit is 500 operations
            items.chunked(500).forEach { chunk ->
                val batch: WriteBatch = firestore.batch()
                chunk.forEach { item ->
                    val docId = item["id"]?.toString()
                        ?: throw IllegalArgumentException("Each item must have an 'id' field")
                    val docRef = collection.document(docId)
                    // Use set with merge to create or update
                    batch.set(docRef, item.filterKeys { it != "id" })
                }
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Download Operations (for restoring to a new device) ---

    /**
     * Download all products for the current user from Firestore.
     */
    suspend fun downloadProducts(): Result<List<Map<String, Any>>> {
        return downloadCollection(COLLECTION_PRODUCTS)
    }

    /**
     * Download all stores for the current user from Firestore.
     */
    suspend fun downloadStores(): Result<List<Map<String, Any>>> {
        return downloadCollection(COLLECTION_STORES)
    }

    /**
     * Download all monthly lists for the current user from Firestore.
     */
    suspend fun downloadMonthlyLists(): Result<List<Map<String, Any>>> {
        return downloadCollection(COLLECTION_MONTHLY_LISTS)
    }

    /**
     * Download all price records for the current user from Firestore.
     */
    suspend fun downloadPriceRecords(): Result<List<Map<String, Any>>> {
        return downloadCollection(COLLECTION_PRICE_RECORDS)
    }

    /**
     * Download all documents from a user's subcollection.
     * Returns each document's data with the document ID included as "id".
     */
    private suspend fun downloadCollection(
        collectionName: String
    ): Result<List<Map<String, Any>>> {
        return try {
            val collection = getUserCollection(collectionName)
            val snapshot = collection.get().await()
            val results = snapshot.documents.map { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                data
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
