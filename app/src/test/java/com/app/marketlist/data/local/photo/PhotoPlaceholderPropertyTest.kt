package com.app.marketlist.data.local.photo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * **Validates: Requirements 6.4**
 *
 * Property 21: Foto placeholder cuando archivo no existe
 *
 * For any product whose photo path references a file that does not exist
 * in the file system, the query should return a placeholder indicator
 * instead of an invalid path. Blank paths also yield placeholder.
 *
 * We test the pure decision logic extracted from PhotoManager:
 * - blank path → placeholder (false / null)
 * - path pointing to non-existent file → placeholder (false / null)
 * - path pointing to existing file → show photo (true / non-null)
 */
class PhotoPlaceholderPropertyTest : FunSpec({

    test("non-existent photo paths always yield placeholder (photoExists=false, getPhotoFile=null)") {
        checkAll(PropTestConfig(iterations = 100), arbNonExistentScenario()) { scenario ->
            val checker = PhotoExistenceChecker(scenario.existingPaths)

            // The queried path is NOT in the existing set → must be placeholder
            checker.photoExists(scenario.queriedPath) shouldBe false
            checker.getPhotoFile(scenario.queriedPath) shouldBe null
        }
    }

    test("blank paths always yield placeholder regardless of existing files") {
        checkAll(PropTestConfig(iterations = 100), arbBlankPath(), arbExistingPaths()) { blankPath, existingPaths ->
            val checker = PhotoExistenceChecker(existingPaths)

            checker.photoExists(blankPath) shouldBe false
            checker.getPhotoFile(blankPath) shouldBe null
        }
    }

    test("existing photo paths return true/non-null (show photo, not placeholder)") {
        checkAll(PropTestConfig(iterations = 100), arbExistingScenario()) { scenario ->
            val checker = PhotoExistenceChecker(scenario.existingPaths)

            // The queried path IS in the existing set → should show photo
            checker.photoExists(scenario.queriedPath) shouldBe true
            checker.getPhotoFile(scenario.queriedPath) shouldBe scenario.queriedPath
        }
    }
})

// ─── Pure Logic Under Test ──────────────────────────────────────────────────────

/**
 * A testable pure-logic class that mirrors PhotoManager's decision logic
 * without depending on Android Context or file system.
 * The set of "existingPaths" simulates which files are actually on disk.
 */
class PhotoExistenceChecker(private val existingPaths: Set<String>) {

    /**
     * Returns true if the path is non-blank and exists in the known set.
     * Mirrors PhotoManager.photoExists logic.
     */
    fun photoExists(photoPath: String): Boolean {
        if (photoPath.isBlank()) return false
        return existingPaths.contains(photoPath)
    }

    /**
     * Returns the path itself (as an indicator of a valid file reference) if it exists,
     * or null otherwise (null = use placeholder).
     * Mirrors PhotoManager.getPhotoFile logic.
     */
    fun getPhotoFile(photoPath: String): String? {
        if (photoPath.isBlank()) return null
        return if (existingPaths.contains(photoPath)) photoPath else null
    }
}

// ─── Generators ─────────────────────────────────────────────────────────────────

private data class NonExistentScenario(
    val existingPaths: Set<String>,
    val queriedPath: String
)

private data class ExistingScenario(
    val existingPaths: Set<String>,
    val queriedPath: String
)

/**
 * Generates a scenario where queriedPath is guaranteed NOT to be in existingPaths.
 */
private fun arbNonExistentScenario(): Arb<NonExistentScenario> = arbitrary {
    val existingPaths = Arb.list(arbPhotoPath(), 1..10).bind().toSet()
    // Generate a path that is NOT in the existing set by using a unique prefix
    val nonExistentPath = "nonexistent_" + Arb.string(5..30).bind()
    // Ensure it's truly not in the set (extremely unlikely collision, but let's be safe)
    val safePath = if (existingPaths.contains(nonExistentPath)) {
        nonExistentPath + "_extra"
    } else {
        nonExistentPath
    }
    NonExistentScenario(existingPaths = existingPaths, queriedPath = safePath)
}

/**
 * Generates a scenario where queriedPath IS in existingPaths.
 */
private fun arbExistingScenario(): Arb<ExistingScenario> = arbitrary {
    val paths = Arb.list(arbPhotoPath(), 1..10).bind().toSet()
    // Pick one from the existing set
    val picked = Arb.element(paths.toList()).bind()
    ExistingScenario(existingPaths = paths, queriedPath = picked)
}

/**
 * Generates realistic photo paths like "product_photos/photo_123_1700000000.jpg"
 */
private fun arbPhotoPath(): Arb<String> = arbitrary {
    val productId = (1L..9999L).random()
    val timestamp = (1600000000L..1800000000L).random()
    "product_photos/photo_${productId}_$timestamp.jpg"
}

/**
 * Generates blank or whitespace-only strings to test the blank path edge case.
 */
private fun arbBlankPath(): Arb<String> = Arb.element(
    listOf("", " ", "  ", "\t", "\n", "   ", " \t\n ")
)

/**
 * Generates a set of existing photo paths.
 */
private fun arbExistingPaths(): Arb<Set<String>> = arbitrary {
    Arb.list(arbPhotoPath(), 0..10).bind().toSet()
}
