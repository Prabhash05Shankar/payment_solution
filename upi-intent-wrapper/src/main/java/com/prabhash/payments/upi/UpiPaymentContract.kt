package com.prabhash.payments.upi

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/** Launches a UPI app and parses its best-effort response; some apps may not return a result. */
class UpiPaymentContract : ActivityResultContract<UpiPaymentRequest, UpiResponse>() {
    override fun createIntent(context: Context, input: UpiPaymentRequest): Intent {
        return Intent.createChooser(UpiIntentFactory.createIntent(input), "Pay using UPI")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): UpiResponse =
        UpiResponseParser.parse(resultCode, intent)
}
