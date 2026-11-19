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
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * **Validates: Requirements 5.2, 9.2**
 *
 * Property 15: Lookup por código de barras
 *
 * For stored barcodes, verify that lookup returns the correct product;
 * for non-stored barcodes, verify that lookup returns null.
 */
class BarcodeLookupPropertyTest : FunSpec({

    test("stored barcode returns correct product") {
        val fakeRepo = FakeBarcodeLookupRepository()
        val scanBarcodeUseCase = ScanBarcodeUseCase(fakeRepo)

        checkAll(PropTestConfig(iterations = 100), arbProductWithBarcode()) { product ->
            // Reset repo for each iteration
            fakeRepo.clear()

            // Store the product
            val result = fakeRepo.createProduct(product)
            result.isSuccess shouldBe true

            val createdId = result.getOrThrow()

            // Lookup by barcode
            val found = scanBarcodeUseCase(product.barcode!!).first()

            // Verify correct product is returned
            found shouldBe product.copy(id = createdId)
        }
    }

    test("non-stored barcode returns null") {
        val fakeRepo = FakeBarcodeLookupRepository()
        val scanBarcodeUseCase = ScanBarcodeUseCase(fakeRepo)

        checkAll(PropTestConfig(iterations = 100), arbValidBarcode(), arbProductWithBarcode()) { lookupBarcode, product ->
            // Reset repo for each iteration
            fakeRepo.clear()

            // Store a product with a DIFFERENT barcode
            val storedBarcode = product.barcode!!
            // Ensure the lookup barcode is different from the stored one
            if (lookupBarcode == storedBarcode) return@checkAll

            fakeRepo.createProduct(product)

            // Lookup a barcode that was NOT stored
            val found = scanBarcodeUseCase(lookupBarcode).first()

            // Should return null
            found shouldBe null
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbValidBarcode(): Arb<String> = arbitrary {
    val length = Arb.int(8..14).bind()
    buildString {
        repeat(length) {
            append(Arb.int(0..9).bind())
        }
    }
}

private fun arbStoreName(): Arb<String> = Arb.string(2..50)

private fun arbStore(): Arb<Store> = arbitrary {
    Store(
        id = Arb.long(1L..1000L).bind(),
        name = arbStoreName().bind(),
        type = Arb.enum<StoreType>().bind(),
        isPredefined = false
    )
}

private fun arbProductWithBarcode(): Arb<Product> = arbitrary {
    val name = Arb.string(2..100).bind()
    val category = Arb.string(1..50).bind()
    val barcode = arbValidBarcode().bind()
    val stores = Arb.list(arbStore(), 1..5).bind()

    Product(
        id = 0,
        name = name,
        category = category,
        barcode = barcode,
        photoPath = null,
        stores = stores
    )
}

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakeBarcodeLookupRepository : ProductRepository {
    private val idGenerator = AtomicLong(1)
    private val productsMap = mutableMapOf<Long, Product>()
    private val productsFlow = MutableStateFlow<Map<Long, Product>>(emptyMap())

    fun clear() {
        productsMap.clear()
        productsFlow.value = emptyMap()
        idGenerator.set(1)
    }

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
