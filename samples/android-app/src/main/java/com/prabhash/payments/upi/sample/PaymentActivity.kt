package com.prabhash.payments.upi.sample

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.prabhash.payments.upi.UpiAttemptSnapshot
import com.prabhash.payments.upi.UpiAttemptState
import com.prabhash.payments.upi.UpiErrorCode
import com.prabhash.payments.upi.UpiIgnoreReason
import com.prabhash.payments.upi.UpiPaymentAttempt
import com.prabhash.payments.upi.UpiPaymentContract
import com.prabhash.payments.upi.UpiPaymentException
import com.prabhash.payments.upi.UpiPaymentRequest
import com.prabhash.payments.upi.UpiPaymentStatus
import com.prabhash.payments.upi.UpiPayments
import com.prabhash.payments.upi.UpiResultBinding
import com.prabhash.payments.upi.UpiResultDispatcher

/**
 * Minimal host screen.
 *
 * A production app receives the payee, amount, and transaction reference from its backend.
 * The values below are placeholders. [upiLauncher] is registered as a property so it exists
 * before the Activity is started. Attempt state is restored from the saved instance before
 * that, including after process death.
 */
class PaymentActivity : ComponentActivity() {
    private val dispatcher = UpiResultDispatcher()
    private val backend: MerchantBackend = UnconfiguredMerchantBackend()
    private lateinit var payButton: Button
    private lateinit var inconclusiveButton: Button
    private lateinit var statusView: TextView

    private val upiLauncher = registerForActivityResult(UpiPaymentContract()) { response ->
        when (val binding = dispatcher.onActivityResult(response)) {
            is UpiResultBinding.Accepted -> showAttempt(binding.attempt)
            is UpiResultBinding.Ignored -> showIgnoredResult(binding.reason)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.toAttemptSnapshot()?.let(dispatcher::restore)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        payButton = Button(this).apply {
            setOnClickListener { startPayment() }
        }
        inconclusiveButton = Button(this).apply {
            text = "I returned without a result"
            setOnClickListener { markReturnedWithoutResult() }
        }
        statusView = TextView(this)
        root.addView(payButton)
        root.addView(inconclusiveButton)
        root.addView(statusView)
        setContentView(root)
        showAttempt(dispatcher.attempt)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAttemptSnapshot(dispatcher.snapshot())
    }

    private fun startPayment() {
        val request = UpiPaymentRequest(
            payeeAddress = "merchant@upi",
            payeeName = "Example Merchant",
            amount = "10.00",
            transactionRef = "ORDER-123",
            transactionNote = "Order payment"
        )
        val errors = request.validate()
        if (errors.isNotEmpty()) {
            statusView.text = errors.joinToString("\n")
            return
        }
        val started = UpiPayments.launch(
            dispatcher = dispatcher,
            launcher = upiLauncher,
            request = request,
            onLaunchError = ::showLaunchError,
            onAttemptRejected = ::showRejectedAttempt
        )
        if (started != null && started.launchError == null) {
            showAttempt(started)
        }
    }

    private fun markReturnedWithoutResult() {
        val id = dispatcher.attempt?.id ?: return
        dispatcher.markInconclusive(id)
        showAttempt(dispatcher.attempt)
    }

    private fun showRejectedAttempt(active: UpiPaymentAttempt) {
        showAttempt(active)
        statusView.text = "A payment for ${active.transactionRef} is already in progress. " +
            "This screen will not start another one. Wait for the UPI app, or record that you returned without a result. " +
            "Neither choice means the payment failed."
    }

    private fun showLaunchError(error: UpiPaymentException) {
        showAttempt(dispatcher.attempt)
        val detail = when (error.code) {
            UpiErrorCode.NO_UPI_APP -> "No compatible UPI app is installed."
            UpiErrorCode.INVALID_REQUEST -> error.message ?: "The payment request is invalid."
            UpiErrorCode.LAUNCH_FAILED -> error.message ?: "The UPI app could not be opened."
        }
        statusView.text = detail +
            "\nThe order was not marked failed. Check your backend if a UPI app may already have opened."
    }

    private fun showIgnoredResult(reason: UpiIgnoreReason) {
        showAttempt(dispatcher.attempt)
        if (reason != UpiIgnoreReason.STALE_REFERENCE && reason != UpiIgnoreReason.UNCORRELATED) return
        statusView.text = "A UPI response arrived, but it could not be matched to the payment on this screen. " +
            "It was not applied. Check the earlier order on your backend.\n\n" +
            statusView.text
    }

    private fun showAttempt(attempt: UpiPaymentAttempt?) {
        val state = attempt?.state ?: UpiAttemptState.IDLE
        val open = state == UpiAttemptState.LAUNCHING || state == UpiAttemptState.WAITING_FOR_RESULT
        payButton.isEnabled = !open
        payButton.text = if (open) "Payment in progress" else "Pay with UPI"
        inconclusiveButton.visibility = if (state == UpiAttemptState.WAITING_FOR_RESULT) View.VISIBLE else View.GONE
        inconclusiveButton.isEnabled = state == UpiAttemptState.WAITING_FOR_RESULT
        statusView.text = messageFor(attempt) ?: "Ready. A response from a UPI app is not a receipt."
    }

    private fun messageFor(attempt: UpiPaymentAttempt?): String? {
        attempt ?: return null
        return when (attempt.state) {
            UpiAttemptState.IDLE -> null
            UpiAttemptState.LAUNCHING, UpiAttemptState.WAITING_FOR_RESULT ->
                "Waiting for the UPI app for ${attempt.transactionRef}. " +
                    "Leaving this screen does not mean the payment failed or was cancelled."
            UpiAttemptState.LAUNCH_FAILED ->
                (attempt.launchError?.message ?: "The UPI app was not opened.") +
                    " Order ${attempt.transactionRef} was not marked failed. You can try again."
            UpiAttemptState.RESULT_UNKNOWN ->
                "No client result for ${attempt.transactionRef}. This is not a failed payment. " +
                    "Check your backend before starting another payment. " +
                    "A delayed UPI reply will be applied to a new attempt only when that reply includes the same transaction reference."
            UpiAttemptState.RESULT_RECEIVED -> clientResultMessage(attempt)
        }
    }

    private fun clientResultMessage(attempt: UpiPaymentAttempt): String {
        val response = attempt.clientResponse
        val headline = when (response?.status) {
            UpiPaymentStatus.SUCCESS ->
                "The UPI app reported success. This does not mean the payment is settled."
            UpiPaymentStatus.FAILURE -> "The UPI app reported failure."
            UpiPaymentStatus.PENDING -> "The UPI app reported that the payment is pending."
            UpiPaymentStatus.CANCELLED_OR_UNKNOWN ->
                "The UPI app reported cancellation. Confirm the order on your backend."
            UpiPaymentStatus.UNKNOWN, null -> "The UPI app did not return a conclusive result."
        }
        val verified = if (response == null) {
            BackendPaymentStatus.UNKNOWN
        } else {
            backend.verifiedStatus(attempt.transactionRef, response)
        }
        return headline +
            "\n\nClient result for ${attempt.transactionRef}." +
            "\nBackend status: ${verified.name}." +
            "\nDo not fulfill the order from the UPI callback."
    }
}

private const val KEY_SAVED = "upi_attempt_saved"
private const val KEY_ID = "upi_attempt_id"
private const val KEY_REF = "upi_attempt_ref"
private const val KEY_STATE = "upi_attempt_state"
private const val KEY_MATCH = "upi_attempt_requires_match"
private const val KEY_ORPHAN = "upi_orphan_callback"
private const val KEY_CLIENT = "upi_client_status"

private fun Bundle.putAttemptSnapshot(snapshot: UpiAttemptSnapshot) {
    putBoolean(KEY_SAVED, true)
    putString(KEY_ID, snapshot.attemptId)
    putString(KEY_REF, snapshot.transactionRef)
    putString(KEY_STATE, snapshot.state.name)
    putBoolean(KEY_MATCH, snapshot.requiresReferenceMatch)
    putBoolean(KEY_ORPHAN, snapshot.orphanCallbackPossible)
    putString(KEY_CLIENT, snapshot.clientStatus?.name)
}

private fun Bundle.toAttemptSnapshot(): UpiAttemptSnapshot? {
    if (!getBoolean(KEY_SAVED, false)) return null
    val stateName = getString(KEY_STATE) ?: return null
    val state = runCatching { UpiAttemptState.valueOf(stateName) }.getOrNull() ?: return null
    val clientName = getString(KEY_CLIENT)
    return UpiAttemptSnapshot(
        attemptId = getString(KEY_ID).orEmpty(),
        transactionRef = getString(KEY_REF).orEmpty(),
        state = state,
        requiresReferenceMatch = getBoolean(KEY_MATCH, false),
        orphanCallbackPossible = getBoolean(KEY_ORPHAN, false),
        clientStatus = clientName?.let { runCatching { UpiPaymentStatus.valueOf(it) }.getOrNull() }
    )
}
