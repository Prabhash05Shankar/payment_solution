package com.prabhash.payments.upi

import android.content.Intent

internal object UpiIntentResponses {
    /** Reads the best-effort payload UPI apps usually return. Does not log it. */
    fun rawResponse(intent: Intent?): String? {
        if (intent == null) return null
        return try {
            sequenceOf("response", "Response")
                .firstNotNullOfOrNull { key -> intent.getStringExtra(key)?.takeIf { it.isNotBlank() } }
                ?: intent.data?.encodedQuery?.takeIf { it.isNotBlank() }
                ?: intent.dataString?.takeIf { it.isNotBlank() }
        } catch (error: RuntimeException) {
            null
        }
    }
}
