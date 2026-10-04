# Integration guide

Register an Activity Result launcher in your Activity:

```kotlin
private val upiLauncher = registerForActivityResult(UpiPaymentContract()) { response ->
    when (response.status) {
        UpiPaymentStatus.SUCCESS -> {
            // Best-effort app response only. Ask your backend to verify the order.
        }
        UpiPaymentStatus.FAILURE -> showMessage("Payment reported as failed")
        UpiPaymentStatus.PENDING -> showMessage("Payment status is pending")
        UpiPaymentStatus.CANCELLED_OR_UNKNOWN,
        UpiPaymentStatus.UNKNOWN -> showMessage("Payment result is not confirmed")
    }
}

// Launch after creating/binding the order on your backend:
upiLauncher.launch(
    UpiPaymentRequest(
        payeeAddress = "merchant@upi",
        payeeName = "Example Merchant",
        amount = "10.00",
        transactionRef = "ORDER-123",
        transactionNote = "Order payment"
    )
)
```

Replace placeholder merchant details with validated values. Ideally the backend creates the order/reference and expected amount. Never use client callback data alone to fulfill an order.

## Status and return behavior
UPI apps can return different fields, omit a callback, or report non-final status. Your app cannot force a third-party UPI app to close. If the user manually returns, do not assume payment did not happen.

For production, verify the final status server-side with the acquiring bank or an authorized payment service provider. Do not embed provider secrets in this Android library. This repository does not implement provider-specific verification.
