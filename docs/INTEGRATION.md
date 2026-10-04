# Integration guide

The Android library launches a UPI app and parses that app's reply. Fulfill an order only after your backend verifies the payment with your acquiring bank or payment provider.

## Android registration

Register the contract while the `ComponentActivity` or `Fragment` is being created, before it is started. A property initializer does that. Registering inside `onStart` or `onResume`, or registering only after a conditional check, throws `IllegalStateException` on the next launch.

```kotlin
class PaymentActivity : ComponentActivity() {
    private val dispatcher = UpiResultDispatcher()

    private val upiLauncher = registerForActivityResult(UpiPaymentContract()) { response ->
        dispatcher.dispatch(response, ::showAttempt)
    }

    private fun startPayment(request: UpiPaymentRequest) {
        UpiPayments.launch(
            dispatcher = dispatcher,
            launcher = upiLauncher,
            request = request,
            onLaunchError = ::showLaunchError,
            onAttemptRejected = ::showRejectedAttempt
        )
    }
}
```

`samples/android-app` uses this launch path and also tells the user when a callback was ignored because it could not be matched. `UpiPayments.launch` catches an invalid request, a missing UPI app, and `ActivityNotFoundException`. Other exceptions propagate. A second launch while one attempt is `LAUNCHING` or `WAITING_FOR_RESULT` does not open another UPI app. `onAttemptRejected` receives the attempt that is still open.

## Attempt states

`UpiResultDispatcher` keeps one attempt:

| State | Meaning |
| --- | --- |
| `IDLE` | Nothing is in progress. |
| `LAUNCHING` | `tryStart` succeeded and the intent has not been confirmed open. |
| `WAITING_FOR_RESULT` | A UPI app was opened. A callback may never arrive. |
| `RESULT_RECEIVED` | An activity result was bound to this attempt. Read `clientResponse`. It is still unverified. |
| `RESULT_UNKNOWN` | The user recorded that they returned without a result, or no callback is available. This is not a failed payment. |
| `LAUNCH_FAILED` | The intent did not open. The order is not marked failed. |

Call `markInconclusive(attemptId)` only from an explicit user action while the state is `WAITING_FOR_RESULT`. Do not call it from a timer. Do not treat that transition, or a missing callback, as payment failure or cancellation.

Retry is a new `UpiPayments.launch` after the state is `RESULT_RECEIVED`, `RESULT_UNKNOWN`, `LAUNCH_FAILED`, or `IDLE`. The sample keeps Pay disabled during `LAUNCHING` and `WAITING_FOR_RESULT`. It shows a separate "I returned without a result" action. Pay is enabled again only after that action, a bound result, or a launch failure. Returning to the screen does not clear the open attempt. The sample creates a new `transactionRef` on every Pay tap. A production app uses the reference issued by its backend instead.

Persist `dispatcher.snapshot()` in `onSaveInstanceState` and call `dispatcher.restore` in `onCreate`, after `super.onCreate` and before the Activity is started. Do not restore in `onStart` or `onResume`. Android delivers a pending activity result when the Activity reaches the started state, so restoring earlier attaches that result to the same attempt after rotation or process death. The sample does this.

The snapshot stores the attempt id, `transactionRef`, state, whether this attempt requires a matching reference, whether an older callback may still arrive, and the parsed client status enum when a result was bound. It does not store `rawResponse`, the launch exception, the transaction id, the approval reference, the response code, or the parser message. A recreated screen can show the client status and still must not treat it as settlement. `isAuthoritativelyVerified` stays false.

If the process is killed before the first `onSaveInstanceState`, both the attempt and Android's pending activity-result record are gone. A callback that still reaches the new screen finds no attempt (`NO_ACTIVE_ATTEMPT`) and is ignored. It does not mark the order paid or failed. The merchant backend reconciles that order by asking the acquiring bank or payment provider for the `transactionRef` it issued. Silence, `RESULT_UNKNOWN`, `LAUNCH_FAILED`, and a client `SUCCESS` are all unresolved until that check.

Android gives every result for one registered launcher to the same callback and does not include this library's attempt id. A UPI app might also omit `txnRef`. Correlation is therefore limited:

- While one attempt is outstanding, a callback is bound to that attempt. A repeated callback is ignored.
- A callback whose `transactionRef` differs from the open attempt is ignored. The open attempt stays unchanged.
- If an attempt ends without a bound result, every later attempt accepts a callback only when `transactionRef` equals that later attempt. A payload with no reference is not applied. This remains true after a later attempt receives a matching result, and it survives restore, because the older callback can still arrive. The requirement clears when that callback is bound to the original attempt before another attempt starts.
- The same `transactionRef` on two attempts is not a correlation key. An old payload that echoes that shared reference is applied to the newer attempt. Create a new reference for every attempt.
- This library cannot prove that an external UPI response belongs to a launch when the app does not return a matching reference.

`UpiPaymentContract.createIntent` throws `UpiPaymentException` before the UPI app opens when the request is invalid or no app can handle `upi://pay`. Calling `upiLauncher.launch(request)` yourself requires the same `try/catch`.

The library does not retain an Activity. Do not store one in your own callback longer than the method call.

## Request fields

| Field | URI key | Rule |
| --- | --- | --- |
| `payeeAddress` | `pa` | Required. `name@handle`, at most 255 characters. Letters, digits, `.`, `_`, `-` before `@`. Letters, digits, `.`, `-` after `@`. |
| `payeeName` | `pn` | Required. At most 99 characters. No control characters. |
| `amount` | `am` | Required. Greater than zero and less than 100000000. `.` separator, at most two decimal places. `10` is sent as `10.00`. |
| `currency` | `cu` | `INR` only. The default is `INR`. |
| `transactionRef` | `tr` | Required by this library. 1 to 35 characters: letters, digits, `.`, `_`, `-`. The backend creates a new value for every attempt. Reusing one lets an old callback complete the newer attempt. |
| `transactionNote` | `tn` | Optional. At most 50 characters. Omitted when blank. `&`, spaces, and other non-control characters are encoded. |

`validate()` returns messages. `fieldErrors()` returns `field`, `code` (`required`, `invalid`, `not_positive`, `precision`, `unsupported`, `too_long`), and `message`.

```kotlin
val request = UpiPaymentRequest(
    payeeAddress = "merchant@upi",
    payeeName = "Example Merchant",
    amount = "10.00",
    transactionRef = "ORDER-123",
    transactionNote = "Order payment"
)
```

Surrounding whitespace on those strings is ignored. Amount formatting does not follow the device locale: `10,50` is invalid in every locale.

The URI is `upi://pay?pa=merchant@upi&pn=Example%20Merchant&am=10.00&cu=INR&tr=ORDER-123&tn=Order%20payment`. `@` is not encoded as `%40`.

`UpiIntentFactory.createUri` and `createIntent` validate and build that intent. They do not check that an app is installed. `createLaunchIntent` does. With one matching app it sets that app's component from `PackageManager`. With several, it opens the system chooser titled "Pay using UPI". Package names are not hardcoded.

The library manifest includes:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="upi" android:host="pay" />
    </intent>
</queries>
```

On Android 11 and newer, a host that omits this cannot see UPI apps. Consuming the library through Gradle merges this element. No extra permission is required.

## Launch failures

| `UpiErrorCode` | When |
| --- | --- |
| `INVALID_REQUEST` | Validation failed, or Android rejected the request as an illegal argument. |
| `NO_UPI_APP` | No activity resolves `upi://pay`, or Android throws `ActivityNotFoundException`. |
| `LAUNCH_FAILED` | The Activity Result launcher was not registered before the Activity started. |
| Attempt rejected | `UpiPayments.launch` calls `onAttemptRejected` when a payment is already `LAUNCHING` or `WAITING_FOR_RESULT`. No new intent is sent. |

Show these as launch problems. Do not mark the order failed or paid because of them. The payer may still complete a payment that was already opened.

## Client result

`UpiResponseParser.parse(resultCode, rawResponse)` interprets the text. `UpiPaymentContract` reads the `response` intent extra, then the intent data query. The activity result code is included in the message when the body is empty. It is not mapped to success, failure, or cancellation.

| Reported status | How it is recognized | How to treat it |
| --- | --- | --- |
| `SUCCESS` | Status text `success` only | The app reported success. Show that it is not a receipt. Ask the backend. |
| `FAILURE` | `failure`, `failed`, `fail` | The app reported failure. Still confirm the order before charging again. |
| `PENDING` | `pending`, `submitted`, `deemed` | The payment may still complete. Poll the backend. |
| `CANCELLED_OR_UNKNOWN` | `cancelled` or `canceled` in the payload | The app reported cancellation. This is not proof that money did not move. |
| `UNKNOWN` | Null, empty, malformed, unrecognized, or response code without a status | Inconclusive. A missing callback is this status, including activity result `0`. |

`submitted` is pending, not success. `responseCode=00` without a success status stays `UNKNOWN`. The first non-blank value wins when a key is repeated. Unknown keys are ignored. URL-encoded values are decoded. A broken `%` sequence does not throw. Keys are matched without case sensitivity. `txnId`, `txnRef` / `refId`, `ApprovalRefNo`, and `responseCode` are copied when present. A success report with no transaction id is still only a client report.

`isAuthoritativelyVerified` is a fixed `false`. It is not a constructor argument. Do not log `rawResponse` in production; it can contain payment details. The library itself does not log it.

Suggested copy:

- Success reported: "The UPI app reported success. This does not mean the payment is settled."
- Failure reported: "The UPI app reported failure."
- Pending: "The UPI app reported that the payment is pending."
- Cancellation reported: "The UPI app reported cancellation. Confirm the order on your backend."
- Unknown: "The UPI app did not return a conclusive result."

Then show the backend status separately.

## Backend verification

There is no backend in this repository. The contract below is illustrative. It is not a bank API, and the paths are examples for your own server. Your payment provider's real API will differ. Do not put provider secrets, salts, or private keys in the Android app.

1. The backend creates an order and a unique `transactionRef`, and stores the expected amount, currency, and merchant identity. Repeating the same create request returns the existing order instead of opening a second one.
2. The app receives the payee, amount, currency, and `transactionRef` from the backend. It does not invent a reference locally for a live order.
3. The app launches the UPI app.
4. The UPI app may return a client payload. The app shows that payload as unverified.
5. The app sends the `transactionRef` and the client fields to the backend. The backend stores them as a report. It does not mark the order paid from `reportedStatus`.
6. The backend asks the acquiring bank or payment provider for the status of that reference. It checks the amount, currency, merchant, and that the reference belongs to this order.
7. The backend updates its payment row idempotently. A repeated verification call for a paid order stays paid and does not ship the order twice.
8. The app reads the verified status from the backend. Pending and delayed provider updates stay pending until the provider's timeout or a later check. Poll or refresh. Do not treat silence from the UPI app as a final failure.
9. `RESULT_UNKNOWN`, a missing callback, `LAUNCH_FAILED`, an ignored callback, and a client status of `SUCCESS` leave the order unresolved. The backend is the only place that may set it paid or failed, and only after the provider check for that same `transactionRef`.

Illustrative create request:

```json
{
  "orderId": "merchant-order-1",
  "amount": "10.00",
  "currency": "INR"
}
```

Illustrative create response:

```json
{
  "transactionRef": "ORDER-123",
  "payeeAddress": "merchant@upi",
  "payeeName": "Example Merchant",
  "amount": "10.00",
  "currency": "INR"
}
```

Illustrative client report. The backend accepts this only as untrusted input:

```json
{
  "transactionRef": "ORDER-123",
  "reportedStatus": "SUCCESS",
  "transactionId": "TXN-1",
  "approvalReference": "APP-9",
  "responseCode": "00"
}
```

Illustrative response to that report:

```json
{
  "transactionRef": "ORDER-123",
  "status": "PENDING_VERIFICATION",
  "verified": false
}
```

Illustrative status read, after the provider confirms the payment:

```json
{
  "transactionRef": "ORDER-123",
  "status": "PAID",
  "amount": "10.00",
  "currency": "INR",
  "verified": true
}
```

`verified: true` in this example means your server checked an authoritative source. The Android model never sets that flag. If the provider later reverses the payment, the backend must update the order again. The Android library does not know about reversals.

The sample's `UnconfiguredMerchantBackend` always returns `UNKNOWN`. Replace it with a real client. Do not implement `verifiedStatus` by returning `PAID` when `clientResponse.status` is `SUCCESS`.

## Error handling checklist

- Show `validate()` errors before launching.
- Show `NO_UPI_APP` as "no UPI app is installed".
- Show `LAUNCH_FAILED` as a launch problem, not as a failed payment.
- Render every `UpiPaymentStatus` without fulfilling the order.
- Send the client result to the backend, then display the backend status.
- Keep pending orders open until the backend reaches a final state or its own timeout.

## Flutter

No Flutter plugin is in this repository. Cross-platform support is not implemented. The host code to add, including `markReturnedWithoutResult`, is in [IMPLEMENTATION.md](IMPLEMENTATION.md).

A plugin should add this Android module to the host app and call it from an `ActivityAware` plugin. Validation and parsing stay in the Kotlin library. The Dart side should not rebuild those rules.

Suggested channel: `com.prabhash.payments.upi`.

Method: `launchPayment`.

Arguments:

```json
{
  "payeeAddress": "merchant@upi",
  "payeeName": "Example Merchant",
  "amount": "10.00",
  "currency": "INR",
  "transactionRef": "ORDER-123",
  "transactionNote": "Order payment"
}
```

Success result, shaped like `UpiResponse`:

```json
{
  "status": "SUCCESS",
  "transactionId": null,
  "transactionRef": "ORDER-123",
  "approvalReference": null,
  "responseCode": null,
  "rawResponse": null,
  "message": "The UPI app reported success. This is not proof that the payment was received.",
  "isAuthoritativelyVerified": false
}
```

`status` is one of `SUCCESS`, `FAILURE`, `PENDING`, `CANCELLED_OR_UNKNOWN`, `UNKNOWN`.

Error result:

```json
{
  "code": "NO_UPI_APP",
  "message": "No compatible UPI application is installed.",
  "fieldErrors": []
}
```

`code` is `INVALID_REQUEST`, `NO_UPI_APP`, `LAUNCH_FAILED`, or `ATTEMPT_IN_PROGRESS`. Use `ATTEMPT_IN_PROGRESS` when the native dispatcher rejects a second launch. For `INVALID_REQUEST`, `fieldErrors` repeats `UpiFieldError`. `UNKNOWN` and an attempt state of `RESULT_UNKNOWN` are normal results, not errors: the payment outcome is inconclusive. Do not map `RESULT_UNKNOWN` to a failed charge.

The plugin needs the foreground Activity because UPI launch uses Activity Result. iOS has no `upi://pay` intent path in this library. Return a separate `UNSUPPORTED` error on iOS rather than pretending the Android call ran. The host app's `<queries>` entry is merged from the Android library only when that library is on the Android Gradle classpath.

Missing work: the Flutter package, Dart API, example app, plugin registration, and tests. None of those exist here.

## React Native

No React Native module is in this repository. The same request, response, and error JSON as the Flutter section should be the contract, so the two hosts do not drift. Step-by-step host code is in [IMPLEMENTATION.md](IMPLEMENTATION.md).

A native module named `UpiIntent` can expose `launchPayment(request): Promise`. Implement it with `ReactApplicationContext`'s current Activity, `UpiPaymentContract`, and one `UpiResultDispatcher` retained across reloads of the UI. Do not copy the parser into JavaScript. Reject the promise with `INVALID_REQUEST`, `NO_UPI_APP`, `LAUNCH_FAILED`, or `ATTEMPT_IN_PROGRESS`. Resolve a missing or unmatched client reply as `status: "UNKNOWN"` instead of rejecting it, and include the attempt state `RESULT_UNKNOWN` when the user left without a callback.

The Android host must depend on `:upi-intent-wrapper`. iOS is unsupported. Package visibility still comes from the library manifest merger.

Missing work: the React Native package, the native module class, JS types, example app, and tests.

## Build and release

The wrapper uses Gradle 8.10.2, Android Gradle Plugin 8.7.3, and Kotlin 2.0.21. Compile SDK is 35. Minimum SDK is 23. Java source and target are 17.

```powershell
.\gradlew.bat --version
.\gradlew.bat clean
.\gradlew.bat test
.\gradlew.bat assemble
```

Run Gradle on JDK 17 or 21. `assemble` builds the library and `samples/android-app`. The sample release variant uses the default unsigned release build; it is an example, not a store listing. No Maven publication is configured. Consume the module as a Gradle project dependency.

Do not commit `.gradle/`, module `build/` directories, `local.properties`, IDE files, or keystores. Those paths are listed in `.gitignore`.
