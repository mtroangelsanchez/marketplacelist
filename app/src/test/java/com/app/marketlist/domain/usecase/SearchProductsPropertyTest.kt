package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.model.StoreType
import com.app.marketlist.domain.repository.ProductRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * **Validates: Requirements 1.7, 9.1**
 *
 * Property 4: Búsqueda parcial case-insensitive
 *
 * For any set of products and any search term (a substring of a product's name),
 * searching should return ALL products whose name or category contains the term
 * (case-insensitive), and NO product in results should be missing the search term
 * from both name and category.
 */
class SearchProductsPropertyTest : FunSpec({

    test("partial search returns all matching products and excludes non-matching ones (case-insensitive)") {
        val fakeRepo = FakeSearchProductRepository()
        val searchUseCase = SearchProductsUseCase(fakeRepo)
        val monthlyListId = 1L

        checkAll(PropTestConfig(iterations = 100), arbProductList()) { products ->
            // Reset and populate repo
            fakeRepo.clear()
            products.forEach { fakeRepo.addProduct(it) }

            // Pick a search term: random substring (2-4 chars) from one product's name
            val sourceProduct = products.random()
            val name = sourceProduct.name
            val substringLength = (2..minOf(4, name.length)).random()
            val startIndex = (0..name.length - substringLength).random()
            val searchTerm = name.substring(startIndex, startIndex + substringLength)

            // Test with original case
            val results = searchUseCase(searchTerm, monthlyListId).first()

            // Compute expected: all products whose name OR category contains term (case-insensitive)
            val expected = products.filter {
                it.name.contains(searchTerm, ignoreCase = true) ||
                    it.category.contains(searchTerm, ignoreCase = true)
            }

            // Verify NO valid product is excluded (completeness)
            results.map { it.id }.toSet() shouldContainAll expected.map { it.id }.toSet()

            // Verify ALL results actually match the search term (soundness)
            results.forEach { product ->
                val matchesName = product.name.contains(searchTerm, ignoreCase = true)
                val matchesCategory = product.category.contains(searchTerm, ignoreCase = true)
                (matchesName || matchesCategory) shouldBe true
            }
        }
    }

    test("search is case-insensitive: uppercase and lowercase variants return same results") {
        val fakeRepo = FakeSearchProductRepository()
        val searchUseCase = SearchProductsUseCase(fakeRepo)
        val monthlyListId = 1L

        checkAll(PropTestConfig(iterations = 100), arbProductList()) { products ->
            // Reset and populate repo
            fakeRepo.clear()
            products.forEach { fakeRepo.addProduct(it) }

            // Pick a search term from a product's name
            val sourceProduct = products.random()
            val name = sourceProduct.name
            val substringLength = (2..minOf(4, name.length)).random()
            val startIndex = (0..name.length - substringLength).random()
            val searchTerm = name.substring(startIndex, startIndex + substringLength)

            // Search with different cases
            val resultsOriginal = searchUseCase(searchTerm, monthlyListId).first()
            val resultsUpper = searchUseCase(searchTerm.uppercase(), monthlyListId).first()
            val resultsLower = searchUseCase(searchTerm.lowercase(), monthlyListId).first()

            // All three should return the same set of product IDs
            resultsOriginal.map { it.id }.toSet() shouldBe resultsUpper.map { it.id }.toSet()
            resultsOriginal.map { it.id }.toSet() shouldBe resultsLower.map { it.id }.toSet()
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

private fun arbProduct(id: Long): Arb<Product> = arbitrary {
    val name = Arb.string(2..50).bind()
    val category = Arb.string(2..30).bind()
    val barcode = Arb.string(8..14).orNull(0.3).bind()
    val photoPath = Arb.string(5..100).orNull(0.5).bind()
    val stores = Arb.list(arbStore(), 1..3).bind()

    Product(
        id = id,
        name = name,
        category = category,
        barcode = barcode,
        photoPath = photoPath,
        stores = stores
    )
}

private fun arbProductList(): Arb<List<Product>> = arbitrary {
    val count = Arb.int(5..20).bind()
    (1..count).map { index ->
        arbProduct(index.toLong()).bind()
    }
}

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakeSearchProductRepository : ProductRepository {
    private val idGenerator = AtomicLong(1)
    private val productsMap = mutableMapOf<Long, Product>()
    private val productsFlow = MutableStateFlow<Map<Long, Product>>(emptyMap())

    fun clear() {
        productsMap.clear()
        productsFlow.value = emptyMap()
        idGenerator.set(1)
    }

    fun addProduct(product: Product) {
        productsMap[product.id] = product
        productsFlow.value = productsMap.toMap()
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
