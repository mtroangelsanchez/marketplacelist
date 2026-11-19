package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.model.StoreType
import com.app.marketlist.domain.repository.ProductRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.coVerify
import io.mockk.mockk

/**
 * Propiedad 2: Validación de campos obligatorios rechaza datos incompletos
 *
 * **Validates: Requirements 1.2**
 *
 * Para cualquier combinación de campos donde nombre, categoría o tienda estén ausentes o vacíos,
 * la operación de creación/edición deberá fallar reportando exactamente los campos faltantes,
 * sin modificar el estado de la base de datos.
 */
class CreateProductValidationPropertyTest : FunSpec({

    val productRepository = mockk<ProductRepository>()
    val createProductUseCase = CreateProductUseCase(productRepository)
    val updateProductUseCase = UpdateProductUseCase(productRepository)

    // Represents an invalid product with metadata about which fields are invalid
    data class InvalidProductInput(
        val product: Product,
        val invalidName: Boolean,
        val invalidCategory: Boolean,
        val emptyStores: Boolean
    )

    // Generator for invalid product inputs (at least one field is invalid)
    val invalidProductArb: Arb<InvalidProductInput> = arbitrary {
        val shouldInvalidateName = Arb.boolean().bind()
        val shouldInvalidateCategory = Arb.boolean().bind()
        val shouldEmptyStores = Arb.boolean().bind()

        // Ensure at least one field is invalid
        val (invalidName, invalidCategory, emptyStores) = if (!shouldInvalidateName && !shouldInvalidateCategory && !shouldEmptyStores) {
            val which = Arb.int(0..2).bind()
            when (which) {
                0 -> Triple(true, false, false)
                1 -> Triple(false, true, false)
                else -> Triple(false, false, true)
            }
        } else {
            Triple(shouldInvalidateName, shouldInvalidateCategory, shouldEmptyStores)
        }

        // Generate name
        val name = if (invalidName) {
            // Invalid names: length < 2 or length > 100 (matching use case validation)
            Arb.element(
                "",              // length 0
                " ",             // length 1
                "a",             // length 1
                "\t",            // length 1
                "\n",            // length 1
                "x".repeat(101)  // length 101 (> 100)
            ).bind()
        } else {
            // Valid name: 2-100 chars
            val base = Arb.string(2..15).bind()
            "P$base".take(100)
        }

        // Generate category
        val category = if (invalidCategory) {
            // Invalid category: empty or blank
            Arb.element("", " ", "   ", "\t", "\n").bind()
        } else {
            // Valid category: non-blank
            Arb.element("Frutas", "Verduras", "Carnes", "Lácteos", "Limpieza", "Bebidas").bind()
        }

        // Generate stores
        val stores = if (emptyStores) {
            emptyList()
        } else {
            // Valid stores: at least one store
            val storeCount = Arb.int(1..3).bind()
            (1..storeCount).map { i ->
                Store(
                    id = i.toLong(),
                    name = "Tienda$i",
                    type = Arb.element(StoreType.SUPERMARKET, StoreType.GENERAL_MARKET).bind(),
                    isPredefined = false
                )
            }
        }

        InvalidProductInput(
            product = Product(
                name = name,
                category = category,
                stores = stores
            ),
            invalidName = invalidName,
            invalidCategory = invalidCategory,
            emptyStores = emptyStores
        )
    }

    test("CreateProductUseCase rejects products with missing/invalid fields and reports exact fields without modifying DB") {
        checkAll(PropTestConfig(iterations = 100), invalidProductArb) { input ->
            val result = createProductUseCase(input.product)

            // The result should be a failure
            result.shouldBeFailure()

            // Extract the AppError
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            val appError = (exception as AppErrorException).appError
            appError.shouldBeInstanceOf<AppError.ValidationError>()

            val reportedFields = (appError as AppError.ValidationError).fields

            // Build expected fields list
            val expectedFields = mutableListOf<String>()
            if (input.invalidName) expectedFields.add("nombre")
            if (input.invalidCategory) expectedFields.add("categoría")
            if (input.emptyStores) expectedFields.add("tienda")

            // Verify exact fields are reported
            reportedFields.sorted() shouldBe expectedFields.sorted()

            // Verify the repository was NOT called (no DB modification)
            coVerify(exactly = 0) { productRepository.createProduct(any()) }
        }
    }

    test("UpdateProductUseCase rejects products with missing/invalid fields and reports exact fields without modifying DB") {
        checkAll(PropTestConfig(iterations = 100), invalidProductArb) { input ->
            // Use an existing product id for update scenario
            val product = input.product.copy(id = 1L)

            val result = updateProductUseCase(product)

            // The result should be a failure
            result.shouldBeFailure()

            // Extract the AppError
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            val appError = (exception as AppErrorException).appError
            appError.shouldBeInstanceOf<AppError.ValidationError>()

            val reportedFields = (appError as AppError.ValidationError).fields

            // Build expected fields list
            val expectedFields = mutableListOf<String>()
            if (input.invalidName) expectedFields.add("nombre")
            if (input.invalidCategory) expectedFields.add("categoría")
            if (input.emptyStores) expectedFields.add("tienda")

            // Verify exact fields are reported
            reportedFields.sorted() shouldBe expectedFields.sorted()

            // Verify the repository was NOT called (no DB modification)
            coVerify(exactly = 0) { productRepository.updateProduct(any()) }
        }
    }
})
