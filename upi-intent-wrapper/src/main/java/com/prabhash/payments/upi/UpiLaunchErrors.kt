package com.prabhash.payments.upi

internal object UpiLaunchErrors {
    const val ACTIVITY_NOT_FOUND = "android.content.ActivityNotFoundException"

    fun classify(
        error: Throwable,
        activityNotFoundClassName: String = ACTIVITY_NOT_FOUND
    ): UpiPaymentException? = when {
        error is UpiPaymentException -> error
        error.javaClass.name == activityNotFoundClassName -> UpiPaymentException(
            UpiErrorCode.NO_UPI_APP,
            "No compatible UPI application is available to complete this payment.",
            error
        )
        error is IllegalStateException -> UpiPaymentException(
            UpiErrorCode.LAUNCH_FAILED,
            "Register UpiPaymentContract with registerForActivityResult before the Activity is started.",
            error
        )
        error is IllegalArgumentException -> UpiPaymentException(
            UpiErrorCode.INVALID_REQUEST,
            error.message ?: "The payment request is invalid.",
            error
        )
        else -> null
    }
}
