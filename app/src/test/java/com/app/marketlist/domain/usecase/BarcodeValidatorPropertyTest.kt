package com.app.marketlist.domain.usecase

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test: Validación de código de barras manual
 *
 * **Validates: Requirements 5.6**
 *
 * Propiedad 16: Para cualquier cadena de caracteres, solo deberá ser aceptada como código
 * de barras manual si contiene exclusivamente dígitos numéricos y su longitud está entre
 * 8 y 14 caracteres inclusive.
 */
class BarcodeValidatorPropertyTest : FunSpec({

    val config = PropTestConfig(iterations = 100)

    // Generator for valid barcodes: strings of 8-14 numeric digits
    val validBarcodeArb: Arb<String> = arbitrary {
        val length = Arb.int(8..14).bind()
        buildString {
            repeat(length) {
                append(Arb.int(0..9).bind())
            }
        }
    }

    // Generator for strings shorter than 8 digits (but all numeric)
    val tooShortNumericArb: Arb<String> = arbitrary {
        val length = Arb.int(0..7).bind()
        buildString {
            repeat(length) {
                append(Arb.int(0..9).bind())
            }
        }
    }

    // Generator for strings longer than 14 digits (but all numeric)
    val tooLongNumericArb: Arb<String> = arbitrary {
        val length = Arb.int(15..30).bind()
        buildString {
            repeat(length) {
                append(Arb.int(0..9).bind())
            }
        }
    }

    // Generator for strings with non-numeric characters of length 8-14
    val nonNumericArb: Arb<String> = arbitrary {
        val length = Arb.int(8..14).bind()
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%^&*()-_=+[]{};:',.<>?/| "
        buildString {
            // Ensure at least one non-digit character
            val nonDigitPos = Arb.int(0 until length).bind()
            repeat(length) { i ->
                if (i == nonDigitPos) {
                    val charIndex = Arb.int(0 until chars.length).bind()
                    append(chars[charIndex])
                } else {
                    append(Arb.int(0..9).bind())
                }
            }
        }
    }

    test("valid barcodes (8-14 numeric digits) are accepted") {
        checkAll(config, validBarcodeArb) { barcode ->
            BarcodeValidator.isValid(barcode) shouldBe true
        }
    }

    test("strings shorter than 8 digits are rejected") {
        checkAll(config, tooShortNumericArb) { barcode ->
            BarcodeValidator.isValid(barcode) shouldBe false
        }
    }

    test("strings longer than 14 digits are rejected") {
        checkAll(config, tooLongNumericArb) { barcode ->
            BarcodeValidator.isValid(barcode) shouldBe false
        }
    }

    test("strings containing non-numeric characters (length 8-14) are rejected") {
        checkAll(config, nonNumericArb) { barcode ->
            BarcodeValidator.isValid(barcode) shouldBe false
        }
    }

    test("empty string is rejected") {
        BarcodeValidator.isValid("") shouldBe false
    }

    test("property: a string is accepted iff it is all digits and length in 8..14") {
        checkAll(config, Arb.string(0..20)) { input ->
            val expected = input.all { it.isDigit() } && input.length in 8..14
            BarcodeValidator.isValid(input) shouldBe expected
        }
    }
})
