package com.prabhash.payments.upi

/**
 * Best-effort status reported by a UPI app.
 *
 * [SUCCESS] means the app reported success. It does not mean the merchant received the money.
 * [CANCELLED_OR_UNKNOWN] is used only when the payload itself says the payment was cancelled.
 * A missing or empty callback is [UNKNOWN], because the payer may still have approved it.
 */
enum class UpiPaymentStatus {
    SUCCESS,
    FAILURE,
    PENDING,
    CANCELLED_OR_UNKNOWN,
    UNKNOWN
}

/**
 * Parsed client response. This is not a receipt.
 *
 * [isAuthoritativelyVerified] is always false. Settlement has to be decided by the merchant backend
 * after it checks the payment with its bank or payment provider. [rawResponse] is kept for
 * debugging and can contain payment data; do not log it in production.
 */
data class UpiResponse(
    val status: UpiPaymentStatus,
    val transactionId: String? = null,
    val transactionRef: String? = null,
    val approvalReference: String? = null,
    val responseCode: String? = null,
    val rawResponse: String? = null,
    val message: String? = null
) {
    val isAuthoritativelyVerified: Boolean
        get() = false
}
