package com.prabhash.payments.upi

import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.util.Locale

object UpiResponseParser {
    fun parse(resultCode: Int, data: Intent?): UpiResponse {
        if (data == null) return UpiResponse(
            status = if (resultCode == Activity.RESULT_CANCELED) UpiPaymentStatus.CANCELLED_OR_UNKNOWN else UpiPaymentStatus.UNKNOWN,
            message = "No response returned by the UPI app"
        )
        val raw = data.getStringExtra("response") ?: data.data?.encodedQuery ?: data.dataString
        val values = parseResponseString(raw)
        val statusText = (values["status"] ?: values["Status"] ?: "").lowercase(Locale.ROOT)
        val status = when (statusText) {
            "success", "submitted" -> UpiPaymentStatus.SUCCESS
            "failure", "failed", "fail" -> UpiPaymentStatus.FAILURE
            "pending" -> UpiPaymentStatus.PENDING
            "cancelled", "canceled" -> UpiPaymentStatus.CANCELLED_OR_UNKNOWN
            else -> if (resultCode == Activity.RESULT_CANCELED) UpiPaymentStatus.CANCELLED_OR_UNKNOWN else UpiPaymentStatus.UNKNOWN
        }
        return UpiResponse(
            status = status,
            transactionId = values["txnId"] ?: values["txn_id"] ?: values["transactionId"],
            approvalReference = values["ApprovalRefNo"] ?: values["approvalRefNo"] ?: values["approvalRef"],
            responseCode = values["responseCode"] ?: values["code"],
            rawResponse = raw,
            message = values["message"] ?: values["Status"],
            isAuthoritativelyVerified = false
        )
    }

    internal fun parseResponseString(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split("&").mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else {
                val key = Uri.decode(part.substring(0, separator)).trim()
                val value = Uri.decode(part.substring(separator + 1)).trim()
                key.takeIf { it.isNotEmpty() }?.let { it to value }
            }
        }.toMap()
    }
}
