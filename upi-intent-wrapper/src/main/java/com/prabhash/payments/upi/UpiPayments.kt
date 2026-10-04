package com.prabhash.payments.upi

import androidx.activity.result.ActivityResultLauncher

/**
 * Starts one payment through [dispatcher] and [launcher].
 *
 * A second call while the attempt is launching or waiting does not launch. [onAttemptRejected]
 * receives the attempt that is still open. Validation failures and Android launch failures are
 * reported to [onLaunchError]. Unexpected exceptions are recorded as [UpiAttemptState.LAUNCH_FAILED]
 * and then rethrown.
 *
 * The activity-result callback can run before this method returns. Feed that callback to
 * [UpiResultDispatcher.dispatch]. This method does not hold an Activity.
 */
object UpiPayments {
    fun launch(
        dispatcher: UpiResultDispatcher,
        launcher: ActivityResultLauncher<UpiPaymentRequest>,
        request: UpiPaymentRequest,
        onLaunchError: (UpiPaymentException) -> Unit,
        onAttemptRejected: (UpiPaymentAttempt) -> Unit
    ): UpiPaymentAttempt? {
        val errors = request.fieldErrors()
        if (errors.isNotEmpty()) {
            onLaunchError(
                UpiPaymentException(
                    UpiErrorCode.INVALID_REQUEST,
                    errors.joinToString("; ") { it.message }
                )
            )
            return null
        }
        val started = when (val start = dispatcher.tryStart(request.transactionRef.trim())) {
            is UpiAttemptStart.Rejected -> {
                onAttemptRejected(start.active)
                return null
            }
            is UpiAttemptStart.Started -> start.attempt
        }
        try {
            launcher.launch(request)
        } catch (error: Exception) {
            val classified = UpiLaunchErrors.classify(error)
            if (classified != null) {
                dispatcher.markLaunchFailed(started.id, classified)
                onLaunchError(classified)
                return dispatcher.attempt
            }
            dispatcher.markLaunchFailed(
                started.id,
                UpiPaymentException(
                    UpiErrorCode.LAUNCH_FAILED,
                    "Unable to launch a UPI application.",
                    error
                )
            )
            throw error
        }
        dispatcher.markWaiting(started.id)
        return dispatcher.attempt
    }
}
