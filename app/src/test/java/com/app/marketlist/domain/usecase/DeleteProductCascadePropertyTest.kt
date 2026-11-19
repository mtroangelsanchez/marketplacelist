package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.repository.PriceRepository
import com.app.marketlist.domain.repository.ProductRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * **Validates: Requirements 1.5, 1.6**
 *
 * Property 3: Eliminación en cascada de productos
 *
 * For any product with N (1-10) associated price records, deleting the product
 * must also remove all associated price records. After deletion, neither the
 * product nor its price records should exist in the repository.
 */
class DeleteProductCascadePropertyTest : FunSpec({

    test("cascade delete: deleting a product removes product and all associated price records") {
        val fakePriceRepo = FakePriceRepository()
        val fakeProductRepo = FakeCascadeProductRepository(fakePriceRepo)
        val deleteUseCase = DeleteProductUseCase(fakeProductRepo)

        checkAll(PropTestConfig(iterations = 100), arbValidProduct(), arbPriceRecordCount()) { product, priceCount ->
            // Reset repos for each iteration
            fakeProductRepo.clear()
            fakePriceRepo.clear()

            // Create product
            val createResult = fakeProductRepo.createProduct(product)
            createResult.isSuccess shouldBe true
            val productId = createResult.getOrThrow()

            // Register N price records for this product
            val priceRecords = generatePriceRecords(productId, priceCount)
            priceRecords.forEach { record ->
                fakePriceRepo.registerPrice(record)
            }

            // Verify price records exist before deletion
            fakePriceRepo.getPriceCountForProduct(productId) shouldBe priceCount

            // Delete product via use case
            val deleteResult = deleteUseCase(productId)
            deleteResult.isSuccess shouldBe true

            // Verify product no longer exists
            fakeProductRepo.getProductById(productId) shouldBe null

            // Verify all price records for that productId are gone
            fakePriceRepo.getPriceCountForProduct(productId) shouldBe 0
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbValidProduct(): Arb<Product> = arbitrary {
    val name = Arb.string(2..100).bind()
    val category = Arb.string(1..50).bind()
    val barcode = Arb.string(8..14).orNull(0.3).bind()
    val photoPath = Arb.string(5..100).orNull(0.5).bind()

    Product(
        id = 0,
        name = name,
        category = category,
        barcode = barcode,
        photoPath = photoPath,
        stores = emptyList()
    )
}

private fun arbPriceRecordCount(): Arb<Int> = Arb.int(1..10)

private fun generatePriceRecords(productId: Long, count: Int): List<PriceRecord> {
    return (1..count).map { index ->
        PriceRecord(
            id = 0,
            productId = productId,
            storeId = index.toLong(),
            monthlyListId = 1L,
            price = 10.0 + index,
            recordDate = LocalDate.now().minusDays(index.toLong())
        )
    }
}

// ─── Fake Repositories ──────────────────────────────────────────────────────────

private class FakePriceRepository : PriceRepository {
    private val idGenerator = AtomicLong(1)
    private val priceRecords = mutableListOf<PriceRecord>()

    fun clear() {
        priceRecords.clear()
        idGenerator.set(1)
    }

    fun getPriceCountForProduct(productId: Long): Int {
        return priceRecords.count { it.productId == productId }
    }

    fun removeRecordsForProduct(productId: Long) {
        priceRecords.removeAll { it.productId == productId }
    }

    override suspend fun registerPrice(priceRecord: PriceRecord): Result<Unit> {
        val id = idGenerator.getAndIncrement()
        priceRecords.add(priceRecord.copy(id = id))
        return Result.success(Unit)
    }

    override fun getPriceHistory(productId: Long, lastNMonths: Int): Flow<List<PriceRecord>> {
        return flowOf(priceRecords.filter { it.productId == productId })
    }

    override fun getPricesForList(monthlyListId: Long): Flow<List<PriceRecord>> {
        return flowOf(priceRecords.filter { it.monthlyListId == monthlyListId })
    }

    override fun getLowestPrice(productId: Long, monthlyListId: Long): Flow<PriceRecord?> {
        return flowOf(
            priceRecords
                .filter { it.productId == productId && it.monthlyListId == monthlyListId }
                .minByOrNull { it.price }
        )
    }
}

private class FakeCascadeProductRepository(
    private val priceRepo: FakePriceRepository
) : ProductRepository {
    private val idGenerator = AtomicLong(1)
    private val productsMap = mutableMapOf<Long, Product>()
    private val productsFlow = MutableStateFlow<Map<Long, Product>>(emptyMap())

    fun clear() {
        productsMap.clear()
        productsFlow.value = emptyMap()
        idGenerator.set(1)
    }

    fun getProductById(id: Long): Product? = productsMap[id]

    override fun getProducts(monthlyListId: Long): Flow<List<Product>> {
        return productsFlow.map { it.values.toList() }
    }

    override fun searchProducts(query: String, monthlyListId: Long): Flow<List<Product>> {
        return productsFlow.map { map ->
            map.values.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
            }
        }
    }

    override fun getProductByBarcode(barcode: String): Flow<Product?> {
        return flowOf(productsMap.values.firstOrNull { it.barcode == barcode })
    }

    override suspend fun createProduct(product: Product): Result<Long> {
        val id = idGenerator.getAndIncrement()
        val stored = product.copy(id = id)
        productsMap[id] = stored
        productsFlow.value = productsMap.toMap()
        return Result.success(id)
    }

    override suspend fun updateProduct(product: Product): Result<Unit> {
        productsMap[product.id] = product
        productsFlow.value = productsMap.toMap()
        return Result.success(Unit)
    }

    override suspend fun deleteProduct(productId: Long): Result<Unit> {
        // Simulate CASCADE behavior: delete product AND associated price records
        productsMap.remove(productId)
        productsFlow.value = productsMap.toMap()
        priceRepo.removeRecordsForProduct(productId)
        return Result.success(Unit)
    }
}
