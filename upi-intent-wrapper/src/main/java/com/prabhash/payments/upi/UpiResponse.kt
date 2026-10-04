package com.prabhash.payments.upi

enum class UpiPaymentStatus { SUCCESS, FAILURE, PENDING, CANCELLED_OR_UNKNOWN, UNKNOWN }

/** Parsed client response only; this is not authoritative payment verification. */
data class UpiResponse(
    val status: UpiPaymentStatus,
    val transactionId: String? = null,
    val approvalReference: String? = null,
    val responseCode: String? = null,
    val rawResponse: String? = null,
    val message: String? = null,
    val isAuthoritativelyVerified: Boolean = false
)
