package com.prabhash.payments.upi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build

/**
 * Creates the Android intent for a validated [UpiPaymentRequest].
 *
 * [createIntent] does not check whether a UPI app is installed. [createLaunchIntent] does, and is
 * what [UpiPaymentContract] uses. Neither method keeps a reference to an Activity.
 */
object UpiIntentFactory {
    /** Validated `upi://pay` URI. Throws [UpiPaymentException] when [UpiPaymentRequest.fieldErrors] is not empty. */
    fun createUri(request: UpiPaymentRequest): Uri = Uri.parse(UpiPayUri.build(request))

    /** Implicit VIEW intent. Does not check for an installed UPI app. */
    fun createIntent(request: UpiPaymentRequest): Intent {
        return Intent(Intent.ACTION_VIEW, createUri(request)).addCategory(Intent.CATEGORY_DEFAULT)
    }

    /**
     * Intent to hand to `startActivity` or an Activity Result launcher.
     *
     * Throws [UpiPaymentException] with [UpiErrorCode.INVALID_REQUEST] or [UpiErrorCode.NO_UPI_APP].
     * One installed handler is launched directly. More than one opens the system chooser. Package
     * names come from the device's package manager, not from a list kept in this library.
     */
    fun createLaunchIntent(context: Context, request: UpiPaymentRequest): Intent {
        val errors = request.fieldErrors()
        if (errors.isNotEmpty()) {
            throw UpiPaymentException(
                UpiErrorCode.INVALID_REQUEST,
                errors.joinToString("; ") { it.message }
            )
        }
        val view = createIntent(request)
        val handlers = queryHandlers(context, view)
        return when (val decision = UpiLaunchPlanner.decide(request, handlers.size)) {
            is UpiLaunchDecision.Invalid -> throw UpiPaymentException(
                UpiErrorCode.INVALID_REQUEST,
                decision.errors.joinToString("; ") { it.message }
            )
            UpiLaunchDecision.NoApplication -> throw UpiPaymentException(
                UpiErrorCode.NO_UPI_APP,
                "No compatible UPI application is installed."
            )
            is UpiLaunchDecision.Ready -> {
                if (!decision.useChooser) {
                    val activity = handlers.singleOrNull()?.activityInfo
                    if (activity != null) {
                        return Intent(view).setClassName(activity.packageName, activity.name)
                    }
                }
                Intent.createChooser(view, "Pay using UPI")
            }
        }
    }
}

private fun queryHandlers(context: Context, view: Intent): List<ResolveInfo> {
    val manager = context.packageManager ?: return emptyList()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.queryIntentActivities(view, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        manager.queryIntentActivities(view, 0)
    }
}
