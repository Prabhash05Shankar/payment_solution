package com.prabhash.payments.upi

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Parses the text a UPI app returned.
 *
 * Apps differ in key casing, separators, and which fields they include. Unknown keys are ignored.
 * The first non-blank value for a duplicated key is kept. A missing body, an empty body, or an unrecognized
 * status becomes [UpiPaymentStatus.UNKNOWN]. The activity result code is not treated as proof of
 * cancellation, failure, or success. Nothing in the parsed result is authoritative.
 *
 * The library does not log [UpiResponse.rawResponse].
 */
object UpiResponseParser {
    fun parse(resultCode: Int, rawResponse: String?): UpiResponse {
        if (rawResponse.isNullOrBlank()) {
            return UpiResponse(
                status = UpiPaymentStatus.UNKNOWN,
                rawResponse = rawResponse,
                message = "The UPI app returned no response (activity result $resultCode). " +
                    "This is not proof of cancellation or failure. Verify the payment with your backend."
            )
        }
        bareStatus(rawResponse)?.let { status ->
            return responseFor(status, rawResponse, emptyMap())
        }
        val values = parseResponseString(rawResponse)
        val status = statusFrom(values["status"])
        if (values.isEmpty() || status == null) {
            return UpiResponse(
                status = UpiPaymentStatus.UNKNOWN,
                transactionId = first(values, TXN_ID_KEYS),
                transactionRef = first(values, TXN_REF_KEYS),
                approvalReference = first(values, APPROVAL_KEYS),
                responseCode = first(values, CODE_KEYS),
                rawResponse = rawResponse,
                message = payloadMessage(values) ?: UNRECOGNIZED_MESSAGE
            )
        }
        return responseFor(status, rawResponse, values)
    }

    /**
     * Query pairs from [raw]. Keys are lower-cased.
     * The first non-blank value for each key is kept. Later duplicates are ignored.
     */
    internal fun parseResponseString(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val query = queryPortion(raw.trim())
        if (query.isEmpty()) return emptyMap()
        val values = LinkedHashMap<String, String>()
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            val separator = part.indexOf('=')
            val rawKey = if (separator < 0) part else part.substring(0, separator)
            val rawValue = if (separator < 0) "" else part.substring(separator + 1)
            val key = decodeComponent(rawKey).trim().lowercase(Locale.ROOT)
            if (key.isEmpty()) continue
            val decoded = decodeComponent(rawValue).trim()
            val existing = values[key]
            if (existing == null || (existing.isBlank() && decoded.isNotEmpty())) {
                values[key] = decoded
            }
        }
        return values
    }
}

private val TXN_ID_KEYS = listOf("txnid", "txn_id", "transactionid")
private val TXN_REF_KEYS = listOf("txnref", "txn_ref", "transactionref", "refid")
private val APPROVAL_KEYS = listOf("approvalrefno", "approvalref")
private val CODE_KEYS = listOf("responsecode", "respcode", "code")

private const val UNRECOGNIZED_MESSAGE =
    "The UPI response did not include a recognized status. Verify the payment with your backend."

private fun responseFor(
    status: UpiPaymentStatus,
    rawResponse: String,
    values: Map<String, String>
): UpiResponse {
    return UpiResponse(
        status = status,
        transactionId = first(values, TXN_ID_KEYS),
        transactionRef = first(values, TXN_REF_KEYS),
        approvalReference = first(values, APPROVAL_KEYS),
        responseCode = first(values, CODE_KEYS),
        rawResponse = rawResponse,
        message = payloadMessage(values) ?: defaultMessage(status)
    )
}

private fun bareStatus(raw: String): UpiPaymentStatus? {
    val trimmed = raw.trim()
    if (trimmed.any { it == '=' || it == '&' || it == '?' }) return null
    return statusFrom(trimmed)
}

private fun statusFrom(text: String?): UpiPaymentStatus? {
    return when (text?.trim()?.lowercase(Locale.ROOT)) {
        "success" -> UpiPaymentStatus.SUCCESS
        "failure", "failed", "fail" -> UpiPaymentStatus.FAILURE
        "pending", "submitted", "deemed" -> UpiPaymentStatus.PENDING
        "cancelled", "canceled" -> UpiPaymentStatus.CANCELLED_OR_UNKNOWN
        else -> null
    }
}

private fun payloadMessage(values: Map<String, String>): String? {
    val message = values["message"]?.trim().orEmpty()
    if (message.isEmpty()) return null
    if (statusFrom(message) != null && message.equals(values["status"]?.trim(), ignoreCase = true)) {
        return null
    }
    return message
}

private fun defaultMessage(status: UpiPaymentStatus): String = when (status) {
    UpiPaymentStatus.SUCCESS ->
        "The UPI app reported success. This is not proof that the payment was received."
    UpiPaymentStatus.FAILURE ->
        "The UPI app reported failure. Verify the order with your backend before retrying."
    UpiPaymentStatus.PENDING ->
        "The UPI app reported a pending payment. Verify the final status with your backend."
    UpiPaymentStatus.CANCELLED_OR_UNKNOWN ->
        "The UPI app reported cancellation. This is not proof that the payment did not complete."
    UpiPaymentStatus.UNKNOWN -> UNRECOGNIZED_MESSAGE
}

private fun first(values: Map<String, String>, keys: List<String>): String? {
    for (key in keys) {
        val value = values[key]
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun queryPortion(raw: String): String {
    val question = raw.indexOf('?')
    if ("://" in raw) {
        return if (question >= 0) raw.substring(question + 1) else ""
    }
    return if (question == 0) raw.substring(1) else raw
}

private fun decodeComponent(value: String): String {
    return try {
        URLDecoder.decode(value, StandardCharsets.UTF_8)
    } catch (error: IllegalArgumentException) {
        value
    }
}
