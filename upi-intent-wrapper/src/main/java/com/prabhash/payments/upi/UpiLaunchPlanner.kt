package com.prabhash.payments.upi

internal sealed class UpiLaunchDecision {
    data class Ready(val uri: String, val useChooser: Boolean) : UpiLaunchDecision()
    data class Invalid(val errors: List<UpiFieldError>) : UpiLaunchDecision()
    data object NoApplication : UpiLaunchDecision()
}

/**
 * Decides whether a request can be handed to Android.
 * [handlerCount] is the number of installed activities that resolve `upi://pay`.
 */
internal object UpiLaunchPlanner {
    fun decide(request: UpiPaymentRequest, handlerCount: Int): UpiLaunchDecision {
        val errors = request.fieldErrors()
        if (errors.isNotEmpty()) return UpiLaunchDecision.Invalid(errors)
        if (handlerCount <= 0) return UpiLaunchDecision.NoApplication
        return UpiLaunchDecision.Ready(
            uri = UpiPayUri.build(request),
            useChooser = handlerCount > 1
        )
    }
}
