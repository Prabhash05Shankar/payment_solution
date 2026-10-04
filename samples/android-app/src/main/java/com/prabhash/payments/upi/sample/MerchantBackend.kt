package com.prabhash.payments.upi.sample

import com.prabhash.payments.upi.UpiResponse

/**
 * Status returned by the merchant backend after it checks an authoritative source.
 * A client [UpiResponse] must not be mapped to [PAID] by itself.
 */
enum class BackendPaymentStatus {
    PAID,
    FAILED,
    PENDING,
    UNKNOWN
}

/**
 * Host-app boundary. Replace [UnconfiguredMerchantBackend] with a client that calls the
 * merchant server. The server checks the order with the merchant's bank or payment provider.
 * This sample does not perform that call and does not contain provider credentials.
 */
fun interface MerchantBackend {
    fun verifiedStatus(transactionRef: String, clientResponse: UpiResponse): BackendPaymentStatus
}

class UnconfiguredMerchantBackend : MerchantBackend {
    override fun verifiedStatus(transactionRef: String, clientResponse: UpiResponse): BackendPaymentStatus {
        return BackendPaymentStatus.UNKNOWN
    }
}
