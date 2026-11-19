package com.app.marketlist.data.remote.firebase

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * **Validates: Requirements 7.5**
 *
 * Property 19: Formato de fecha de sincronización
 *
 * For any valid LocalDateTime, the display format for "last sync time"
 * must strictly match the pattern "dd/MM/yyyy HH:mm".
 */
class SyncDateFormatPropertyTest : FunSpec({

    val formatter = SyncDateFormatter()

    test("any valid LocalDateTime formatted for sync display matches dd/MM/yyyy HH:mm pattern") {
        checkAll(PropTestConfig(iterations = 100), arbValidLocalDateTime()) { dateTime ->
            val result = formatter.format(dateTime)

            // Verify output matches regex pattern
            result shouldMatch Regex("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}")

            // Verify output has exactly 16 characters
            result.length shouldBe 16

            // Verify round-trip: parsing back produces the same date/time (truncated to minutes)
            val parsedBack = LocalDateTime.parse(result, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            parsedBack.year shouldBe dateTime.year
            parsedBack.monthValue shouldBe dateTime.monthValue
            parsedBack.dayOfMonth shouldBe dateTime.dayOfMonth
            parsedBack.hour shouldBe dateTime.hour
            parsedBack.minute shouldBe dateTime.minute
        }
    }

    test("known example: 2024-03-05 09:07 formats as 05/03/2024 09:07") {
        val dateTime = LocalDateTime.of(2024, 3, 5, 9, 7)
        val result = formatter.format(dateTime)
        result shouldBe "05/03/2024 09:07"
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbValidLocalDateTime(): Arb<LocalDateTime> = arbitrary {
    val year = Arb.int(2020..2030).bind()
    val month = Arb.int(1..12).bind()
    val day = Arb.int(1..28).bind()
    val hour = Arb.int(0..23).bind()
    val minute = Arb.int(0..59).bind()

    LocalDateTime.of(year, month, day, hour, minute)
}
