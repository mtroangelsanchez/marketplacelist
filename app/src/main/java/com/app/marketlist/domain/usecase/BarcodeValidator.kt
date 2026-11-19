package com.app.marketlist.domain.usecase

/**
 * Validates manual barcode input according to the business rule:
 * A valid barcode consists exclusively of numeric digits (0-9)
 * and has a length between 8 and 14 characters inclusive.
 *
 * This validation applies when the barcode scanner fails after timeout
 * and the user enters a barcode manually.
 */
object BarcodeValidator {

    private val VALID_BARCODE_REGEX = Regex("^\\d{8,14}$")

    /**
     * Returns true if the given string is a valid manual barcode input:
     * - Contains only numeric digits (0-9)
     * - Has a length between 8 and 14 characters inclusive
     */
    fun isValid(barcode: String): Boolean {
        return VALID_BARCODE_REGEX.matches(barcode)
    }
}
