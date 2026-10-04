package com.prabhash.payments.upi

/**
 * Builds an `upi://pay` URI without using Android `Uri.Builder`.
 *
 * `Uri.Builder.appendQueryParameter` percent-encodes `@`. Several UPI apps then fail to read the
 * payee address. This encoder leaves `@` and unreserved characters as-is and percent-encodes
 * everything else, including spaces, so values are never concatenated raw.
 */
internal object UpiPayUri {
    fun build(request: UpiPaymentRequest): String {
        val errors = request.fieldErrors()
        if (errors.isNotEmpty()) {
            throw UpiPaymentException(
                UpiErrorCode.INVALID_REQUEST,
                errors.joinToString("; ") { it.message }
            )
        }
        val params = mutableListOf(
            "pa" to request.payeeAddress.trim(),
            "pn" to request.payeeName.trim(),
            "am" to canonicalAmount(request.amount),
            "cu" to "INR",
            "tr" to request.transactionRef.trim()
        )
        val note = request.transactionNote?.trim().orEmpty()
        if (note.isNotEmpty()) {
            params += "tn" to note
        }
        val query = params.joinToString("&") { (key, value) ->
            "${encodeQueryComponent(key)}=${encodeQueryComponent(value)}"
        }
        return "upi://pay?$query"
    }
}

internal fun encodeQueryComponent(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val encoded = StringBuilder(bytes.size + 8)
    for (byte in bytes) {
        val unsigned = byte.toInt() and 0xFF
        if (isUnreserved(unsigned)) {
            encoded.append(unsigned.toChar())
        } else {
            encoded.append('%')
            encoded.append(HEX[unsigned ushr 4])
            encoded.append(HEX[unsigned and 0x0F])
        }
    }
    return encoded.toString()
}

private const val HEX = "0123456789ABCDEF"

private fun isUnreserved(unsigned: Int): Boolean {
    val character = unsigned.toChar()
    return character in 'A'..'Z' ||
        character in 'a'..'z' ||
        character in '0'..'9' ||
        character == '-' ||
        character == '.' ||
        character == '_' ||
        character == '~' ||
        character == '@'
}
