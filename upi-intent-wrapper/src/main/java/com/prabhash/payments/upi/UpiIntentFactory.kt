package com.prabhash.payments.upi

import android.content.Intent
import android.net.Uri

object UpiIntentFactory {
    fun createUri(request: UpiPaymentRequest): Uri {
        val errors = request.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return Uri.Builder()
            .scheme("upi").authority("pay")
            .appendQueryParameter("pa", request.payeeAddress)
            .appendQueryParameter("pn", request.payeeName)
            .appendQueryParameter("am", request.amount)
            .appendQueryParameter("cu", request.currency)
            .appendQueryParameter("tr", request.transactionRef)
            .apply { request.transactionNote?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("tn", it) } }
            .build()
    }

    fun createIntent(request: UpiPaymentRequest): Intent = Intent(Intent.ACTION_VIEW, createUri(request))
}
