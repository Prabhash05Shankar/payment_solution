package com.prabhash.payments.upi

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Merchant payment request for an `upi://pay` intent.
 *
 * The amount and [transactionRef] are required. Static QR codes may omit an amount, and NPCI marks
 * `tr` as conditional, but this library only starts a payment for a known order that the merchant
 * backend can reconcile.
 *
 * [fieldErrors] documents every limit that is stricter than "not blank".
 */
data class UpiPaymentRequest(
    val payeeAddress: String,
    val payeeName: String,
    val amount: String,
    val transactionRef: String,
    val transactionNote: String? = null,
    val currency: String = "INR"
) {
    /** Human-readable validation errors. Empty when the request can be launched. */
    fun validate(): List<String> = fieldErrors().map { it.message }

    /**
     * Structured validation errors.
     *
     * - `payeeAddress`: required, at most 255 characters, shaped like `name@handle`. The name part
     *   allows letters, digits, `.`, `_`, and `-`. The handle allows letters, digits, `.`, and `-`.
     * - `payeeName`: required, at most 99 Unicode code points, no control characters.
     * - `amount`: required, greater than zero, less than 100000000, using `.` as the separator and
     *   at most two decimal places. Grouping separators and exponents are rejected.
     * - `transactionRef`: required, 1 to 35 characters from `A-Z`, `a-z`, `0-9`, `.`, `_`, and `-`.
     * - `currency`: `INR` only.
     * - `transactionNote`: optional, at most 50 Unicode code points, no control characters.
     *   Characters such as `&` and spaces are allowed; the URI builder encodes them.
     */
    fun fieldErrors(): List<UpiFieldError> = buildList {
        addAll(payeeAddressErrors(payeeAddress))
        addAll(payeeNameErrors(payeeName))
        addAll(amountErrors(amount))
        addAll(transactionRefErrors(transactionRef))
        addAll(currencyErrors(currency))
        addAll(transactionNoteErrors(transactionNote))
    }
}

/** One validation failure. [code] is stable; [message] is for developers. */
data class UpiFieldError(
    val field: String,
    val code: String,
    val message: String
)

internal fun canonicalAmount(amount: String): String {
    return BigDecimal(amount.trim()).setScale(2, RoundingMode.UNNECESSARY).toPlainString()
}

private const val MAX_PAYEE_ADDRESS_LENGTH = 255
private const val MAX_PAYEE_NAME_CODE_POINTS = 99
private const val MAX_NOTE_CODE_POINTS = 50
private const val MAX_AMOUNT_INTEGER_DIGITS = 8

private val PAYEE_ADDRESS = Regex("^[A-Za-z0-9.\\-_]{2,249}@[A-Za-z0-9][A-Za-z0-9.\\-]{1,63}$")
private val TRANSACTION_REF = Regex("^[A-Za-z0-9._\\-]{1,35}$")
private val DECIMAL_AMOUNT = Regex("\\d+(\\.\\d+)?")

private fun payeeAddressErrors(value: String): List<UpiFieldError> {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return listOf(UpiFieldError("payeeAddress", "required", "payeeAddress is required."))
    }
    if (trimmed.length > MAX_PAYEE_ADDRESS_LENGTH || !PAYEE_ADDRESS.matches(trimmed)) {
        return listOf(
            UpiFieldError(
                "payeeAddress",
                "invalid",
                "payeeAddress must be a UPI ID such as name@bank, using letters, digits, '.', '_' or '-' before '@'."
            )
        )
    }
    return emptyList()
}

private fun payeeNameErrors(value: String): List<UpiFieldError> {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return listOf(UpiFieldError("payeeName", "required", "payeeName is required."))
    }
    if (trimmed.any { it.isISOControl() }) {
        return listOf(UpiFieldError("payeeName", "invalid", "payeeName must not contain control characters."))
    }
    if (trimmed.codePointCount(0, trimmed.length) > MAX_PAYEE_NAME_CODE_POINTS) {
        return listOf(UpiFieldError("payeeName", "too_long", "payeeName must be at most 99 characters."))
    }
    return emptyList()
}

private fun amountErrors(amount: String): List<UpiFieldError> {
    val value = amount.trim()
    if (value.isEmpty()) {
        return listOf(UpiFieldError("amount", "required", "amount is required."))
    }
    if (value.startsWith("-")) {
        return listOf(UpiFieldError("amount", "not_positive", "amount must be greater than zero."))
    }
    if (!DECIMAL_AMOUNT.matches(value)) {
        return listOf(
            UpiFieldError(
                "amount",
                "invalid",
                "amount must be a decimal number that uses '.' as the separator, such as 10.00."
            )
        )
    }
    val fraction = value.substringAfter('.', "")
    if (fraction.length > 2) {
        return listOf(UpiFieldError("amount", "precision", "amount must have at most two decimal places."))
    }
    val integerDigits = value.substringBefore('.').trimStart('0').ifEmpty { "0" }
    if (integerDigits.length > MAX_AMOUNT_INTEGER_DIGITS) {
        return listOf(UpiFieldError("amount", "invalid", "amount must be less than 100000000."))
    }
    if (BigDecimal(value).compareTo(BigDecimal.ZERO) <= 0) {
        return listOf(UpiFieldError("amount", "not_positive", "amount must be greater than zero."))
    }
    return emptyList()
}

private fun transactionRefErrors(value: String): List<UpiFieldError> {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return listOf(UpiFieldError("transactionRef", "required", "transactionRef is required."))
    }
    if (!TRANSACTION_REF.matches(trimmed)) {
        return listOf(
            UpiFieldError(
                "transactionRef",
                "invalid",
                "transactionRef must be 1 to 35 characters and use only letters, digits, '.', '_' or '-'."
            )
        )
    }
    return emptyList()
}

private fun currencyErrors(value: String): List<UpiFieldError> {
    if (value.trim() != "INR") {
        return listOf(UpiFieldError("currency", "unsupported", "currency must be INR."))
    }
    return emptyList()
}

private fun transactionNoteErrors(value: String?): List<UpiFieldError> {
    if (value == null) return emptyList()
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (trimmed.any { it.isISOControl() }) {
        return listOf(UpiFieldError("transactionNote", "invalid", "transactionNote must not contain control characters."))
    }
    if (trimmed.codePointCount(0, trimmed.length) > MAX_NOTE_CODE_POINTS) {
        return listOf(UpiFieldError("transactionNote", "too_long", "transactionNote must be at most 50 characters."))
    }
    return emptyList()
}
