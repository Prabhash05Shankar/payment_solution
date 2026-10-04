package com.prabhash.payments.upi

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * Activity Result contract for one UPI payment attempt.
 *
 * Register it with `registerForActivityResult` while the Activity or Fragment is being created,
 * before it is started. Registering later throws [IllegalStateException]. Registering as a
 * property initializer is the usual pattern:
 *
 * ```
 * private val upiLauncher = registerForActivityResult(UpiPaymentContract()) { response ->
 *     dispatcher.dispatch(response, ::onClientResponse)
 * }
 * ```
 *
 * [createIntent] throws [UpiPaymentException] for an invalid request or when no UPI app is
 * installed. [parseResult] reads a best-effort client payload. Some apps return nothing, and a
 * system chooser can drop the real result. Use [UpiPayments.launch] with a [UpiResultDispatcher].
 * The dispatcher rejects a second launch while one attempt is open, and it does not attach a
 * callback to a newer attempt unless the payload's transaction reference matches that attempt.
 *
 * This contract does not store an Activity.
 */
class UpiPaymentContract : ActivityResultContract<UpiPaymentRequest, UpiResponse>() {
    override fun createIntent(context: Context, input: UpiPaymentRequest): Intent {
        return UpiIntentFactory.createLaunchIntent(context, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): UpiResponse {
        return UpiResponseParser.parse(resultCode, UpiIntentResponses.rawResponse(intent))
    }
}
