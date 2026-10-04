package com.prabhash.payments.upi

import java.math.BigDecimal

data class UpiPaymentRequest(
    val payeeAddress: String,
    val payeeName: String,
    val amount: String,
    val transactionRef: String,
    val transactionNote: String? = null,
    val currency: String = "INR"
) {
    fun validate(): List<String> = buildList {
        if (payeeAddress.isBlank() || !payeeAddress.contains("@")) add("payeeAddress must be a valid-looking UPI ID")
        if (payeeName.isBlank()) add("payeeName must not be blank")
        val parsed = amount.toBigDecimalOrNull()
        if (parsed == null || parsed <= BigDecimal.ZERO || parsed.scale() > 2) add("amount must be positive with at most two decimal places")
        if (transactionRef.isBlank()) add("transactionRef must not be blank")
        if (currency != "INR") add("Only INR is supported")
    }
}
