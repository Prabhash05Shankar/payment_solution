package com.prabhash.payments.upi

/**
 * Failure while validating or launching a UPI intent.
 *
 * [UpiErrorCode.INVALID_REQUEST] means the request never launched.
 * [UpiErrorCode.NO_UPI_APP] means no compatible application could be opened.
 * [UpiErrorCode.LAUNCH_FAILED] means the request was valid but Android rejected the launch.
 * None of these codes say whether money moved. Ask the merchant backend.
 */
class UpiPaymentException(
    val code: UpiErrorCode,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/** Stable codes for Android, Flutter, and React Native callers. */
enum class UpiErrorCode {
    INVALID_REQUEST,
    NO_UPI_APP,
    LAUNCH_FAILED
}
