package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.model.StoreType
import com.app.marketlist.domain.repository.ProductRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * **Validates: Requirements 1.1, 1.3**
 *
 * Property 1: Round-trip de persistencia de productos
 *
 * For any product with a valid name (2-100 chars), non-empty category,
 * and at least one store, creating it and then reading it back should
 * return a product with the same data.
 */
class CreateProductRoundTripPropertyTest : FunSpec({

    test("round-trip: creating a product and reading it back preserves all data") {
        val fakeRepo = FakeProductRepository()
        val createUseCase = CreateProductUseCase(fakeRepo)

        checkAll(PropTestConfig(iterations = 100), arbValidProduct()) { product ->
            // Reset repo for each iteration
            fakeRepo.clear()

            // Create product via use case
            val result = createUseCase(product)
            result.isSuccess shouldBe true

            val createdId = result.getOrThrow()

            // Read back from repository
            val stored = fakeRepo.getProductById(createdId)

            // Verify round-trip: same data
            stored!!.name shouldBe product.name
            stored.category shouldBe product.category
            stored.barcode shouldBe product.barcode
            stored.photoPath shouldBe product.photoPath
            stored.stores.map { it.id }.toSet() shouldBe product.stores.map { it.id }.toSet()
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbStoreName(): Arb<String> = Arb.string(2..50)

private fun arbStore(): Arb<Store> = arbitrary {
    Store(
        id = Arb.long(1L..1000L).bind(),
        name = arbStoreName().bind(),
        type = Arb.enum<StoreType>().bind(),
        isPredefined = false
    )
}

private fun arbValidProduct(): Arb<Product> = arbitrary {
    val name = Arb.string(2..100).bind()
    val category = Arb.string(1..50).bind()
    val barcode = Arb.string(8..14).orNull(0.3).bind()
    val photoPath = Arb.string(5..100).orNull(0.5).bind()
    val stores = Arb.list(arbStore(), 1..5).bind()

    Product(
        id = 0,
        name = name,
        category = category,
        barcode = barcode,
        photoPath = photoPath,
        stores = stores
    )
}

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakeProductRepository : ProductRepository {
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
        productsMap.remove(productId)
        productsFlow.value = productsMap.toMap()
        return Result.success(Unit)
    }
}
