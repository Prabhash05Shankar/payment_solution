# Implementation

This repository ships an Android library, `:upi-intent-wrapper`, and a native sample at `samples/android-app`. Flutter and React Native packages are not in this repository. The sections below are the host code you add. Validation, URI encoding, app discovery, parsing, and the attempt lifecycle stay in the Kotlin library. Do not reimplement them in Dart or JavaScript.

A client `SUCCESS` is not a receipt. `UpiResponse.isAuthoritativelyVerified` is always false. Fulfill an order only after your backend checks that attempt's `transactionRef` with your acquiring bank or payment provider. Create a new `transactionRef` for every attempt. Reusing one lets an old UPI reply complete the newer attempt.

iOS has no `upi://pay` path here. A Flutter or React Native iOS build returns `UNSUPPORTED` and does not pretend the Android call ran.

Package name: `com.prabhash.payments.upi`.

## Shared contract

Request fields:

| Field | Required | Rule |
| --- | --- | --- |
| `payeeAddress` | yes | `name@handle`, at most 255 characters |
| `payeeName` | yes | at most 99 characters, no control characters |
| `amount` | yes | greater than 0 and less than 100000000, `.` separator, at most two decimal places |
| `currency` | no | `INR` only. Default `INR` |
| `transactionRef` | yes | 1 to 35 characters: letters, digits, `.`, `_`, `-`. New value per attempt, issued by the backend |
| `transactionNote` | no | at most 50 characters, no control characters |

Launch errors are `INVALID_REQUEST`, `NO_UPI_APP`, and `LAUNCH_FAILED`. A second launch while one attempt is opening or waiting is `ATTEMPT_IN_PROGRESS` on the Flutter and React Native bridges. In Kotlin that rejection is `UpiAttemptStart.Rejected`, not a `UpiErrorCode`. None of these mean the payment failed.

Client `status` is `SUCCESS`, `FAILURE`, `PENDING`, `CANCELLED_OR_UNKNOWN`, or `UNKNOWN`. `RESULT_UNKNOWN` is an attempt state for an explicit "I returned without a result" action. It is not an error and it is not a failed payment. Resolve it. Do not reject the bridge call.

Attempt states: `IDLE`, `LAUNCHING`, `WAITING_FOR_RESULT`, `RESULT_RECEIVED`, `RESULT_UNKNOWN`, `LAUNCH_FAILED`.

Keep one `UpiResultDispatcher` for the screen. Register `UpiPaymentContract` with `registerForActivityResult` before the Activity is started. Restore `dispatcher.snapshot()` in `onCreate` after `super.onCreate` and before the Activity is started. Android delivers a pending result when the Activity reaches the started state.

## Android native

The compiling example is `samples/android-app`. This repository already includes it:

```kotlin
include(":upi-intent-wrapper")
include(":sample")
project(":sample").projectDir = file("samples/android-app")
```

The sample depends only on the library:

```kotlin
dependencies {
    implementation(project(":upi-intent-wrapper"))
}
```

In another app, point `projectDir` at `upi-intent-wrapper` and use the same dependency. `minSdk` must be 23 or higher. The library manifest merges a `<queries>` intent for `upi://pay`. No extra permission is required. Build with JDK 17 or 21. The wrapper uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, and Java 17.

Your backend returns `payeeAddress`, `payeeName`, `amount`, and a new `transactionRef`. The Activity launches that request.

```kotlin
class CheckoutActivity : ComponentActivity() {
    private val dispatcher = UpiResultDispatcher()

    private val upiLauncher = registerForActivityResult(UpiPaymentContract()) { response ->
        when (val binding = dispatcher.onActivityResult(response)) {
            is UpiResultBinding.Accepted -> showClientReport(binding.attempt)
            is UpiResultBinding.Ignored -> showIgnored(binding.reason)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.toAttemptSnapshot()?.let(dispatcher::restore)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAttemptSnapshot(dispatcher.snapshot())
    }

    private fun pay(request: UpiPaymentRequest) {
        val open = dispatcher.state == UpiAttemptState.LAUNCHING ||
            dispatcher.state == UpiAttemptState.WAITING_FOR_RESULT
        if (open) return
        UpiPayments.launch(
            dispatcher = dispatcher,
            launcher = upiLauncher,
            request = request,
            onLaunchError = { error -> showLaunchProblem(error) },
            onAttemptRejected = { active -> showStillWaiting(active) }
        )
    }

    private fun userReturnedWithoutResult() {
        val id = dispatcher.attempt?.id ?: return
        dispatcher.markInconclusive(id)
    }
}
```

Disable the pay control while the state is `LAUNCHING` or `WAITING_FOR_RESULT`. `onResume` must not turn it back on. Offer a separate action that calls `markInconclusive` only while the state is `WAITING_FOR_RESULT`. Do not call it from a timer.

Send `attempt.clientResponse` to the backend as an untrusted report. Display the backend status separately. `samples/android-app` uses `UnconfiguredMerchantBackend`, which always returns `UNKNOWN`. Replace that with your server client. Do not return paid when `clientResponse.status` is `SUCCESS`.

Persist the snapshot with the same fields as the sample: attempt id, `transactionRef`, state name, `requiresReferenceMatch`, `orphanCallbackPossible`, and the client status name. The keys in `PaymentActivity.kt` are `upi_attempt_saved`, `upi_attempt_id`, `upi_attempt_ref`, `upi_attempt_state`, `upi_attempt_requires_match`, `upi_orphan_callback`, and `upi_client_status`. The snapshot does not include `rawResponse`. Do not log `rawResponse`.

If the process dies before the first `onSaveInstanceState`, a later callback has no attempt and is ignored. The backend still checks the reference it issued.

`.\gradlew.bat assemble` writes `samples/android-app/build/outputs/apk/debug/sample-debug.apk`. That sample uses placeholder payee details and a random reference. It is not a production checkout.

## Flutter

No Flutter plugin, Dart API, example, or test is in this repository. Add the bridge in the Flutter app, or in a plugin you publish separately.

The Android host must compile `:upi-intent-wrapper`. From the Flutter project:

```gradle
// android/settings.gradle
include ":upi-intent-wrapper"
project(":upi-intent-wrapper").projectDir = new File(settingsDir, "../../payment_solution/upi-intent-wrapper")
```

```gradle
// android/app/build.gradle
dependencies {
    implementation project(":upi-intent-wrapper")
}
```

Adjust the relative path to your checkout. The Flutter Android Gradle plugin must be able to build a module that uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, and Java 17. `minSdk` must be 23 or higher. Package visibility is merged from the library.

Use two methods on channel `com.prabhash.payments.upi`:

| Method | When the Dart call completes |
| --- | --- |
| `launchPayment` | Launch error, overlapping attempt, or a client report bound to this attempt |
| `markReturnedWithoutResult` | The user says they came back and no callback arrived |

`launchPayment` arguments are the request fields above. A bound report resolves with:

```json
{
  "attemptId": "generated-by-the-library",
  "attemptState": "RESULT_RECEIVED",
  "status": "SUCCESS",
  "transactionId": null,
  "transactionRef": "ORDER-123",
  "approvalReference": null,
  "responseCode": null,
  "message": "The UPI app reported success. This is not proof that the payment was received.",
  "isAuthoritativelyVerified": false
}
```

Omit `rawResponse` from the map you send to Dart. `markReturnedWithoutResult` resolves with `attemptState: "RESULT_UNKNOWN"` and `status: "UNKNOWN"`. Launch failures throw `PlatformException` with `code` set to `INVALID_REQUEST`, `NO_UPI_APP`, `LAUNCH_FAILED`, or `ATTEMPT_IN_PROGRESS`. For `INVALID_REQUEST`, put `fieldErrors` in `details` as `{ "field", "code", "message" }`.

Register the Activity Result launcher before the Activity is started. `MainActivity` is the right place. Plugin attachment during `FlutterActivity.onCreate` is also before start on a cold launch. Keep the dispatcher on the Activity so a UI reload does not create a second one.

```kotlin
class MainActivity : FlutterActivity() {
    val dispatcher = UpiResultDispatcher()
    private var pending: MethodChannel.Result? = null

    private val upiLauncher = registerForActivityResult(UpiPaymentContract()) { response ->
        when (val binding = dispatcher.onActivityResult(response)) {
            is UpiResultBinding.Accepted -> pending?.success(binding.attempt.toMap())
            is UpiResultBinding.Ignored -> Unit
        }
        pending = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.toAttemptSnapshot()?.let(dispatcher::restore)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAttemptSnapshot(dispatcher.snapshot())
    }
}
```

`configureFlutterEngine` installs the channel and calls `UpiPayments.launch` with `upiLauncher`. `onLaunchError` completes `result.error`. `onAttemptRejected` completes `result.error("ATTEMPT_IN_PROGRESS", ...)`. `markReturnedWithoutResult` calls `dispatcher.markInconclusive` and resolves the pending result with `RESULT_UNKNOWN`. Restore runs after `super.onCreate`, which is still before the Activity is started, so a pending UPI result binds to the restored attempt.

Dart side:

```dart
const _channel = MethodChannel('com.prabhash.payments.upi');

Future<Map<String, dynamic>> launchPayment(Map<String, String> request) async {
  final raw = await _channel.invokeMapMethod<String, dynamic>('launchPayment', request);
  return raw ?? const {};
}

Future<Map<String, dynamic>> markReturnedWithoutResult() async {
  final raw = await _channel.invokeMapMethod<String, dynamic>('markReturnedWithoutResult');
  return raw ?? const {};
}
```

Disable the pay button in Flutter while `attemptState` is `LAUNCHING` or `WAITING_FOR_RESULT`. Show a separate control that calls `markReturnedWithoutResult`. Send the resolved map to your backend. Do not treat `status == SUCCESS` or `isAuthoritativelyVerified` as settlement. On iOS, the method throws `UNSUPPORTED`.

Missing until you add them: the plugin package, `pubspec.yaml`, Android and iOS registrations, the Dart API, an example app, and tests.

## React Native

No React Native package, native module, JavaScript types, example, or test is in this repository. Use the same JSON contract as Flutter so the hosts do not drift.

Add `:upi-intent-wrapper` to the Android app's Gradle settings and `implementation project(":upi-intent-wrapper")`. `minSdk` must be 23 or higher.

Do not call `registerForActivityResult` from a native-module constructor or from a button handler. Those run too late. `ReactActivity` is a `ComponentActivity`. Register the launcher as a property on `MainActivity`, and keep one `UpiResultDispatcher` there so a JavaScript reload does not drop the open attempt.

```kotlin
class MainActivity : ReactActivity(), UpiHost {
    override val dispatcher = UpiResultDispatcher()
    private var pending: Promise? = null

    override val upiLauncher = registerForActivityResult(UpiPaymentContract()) { response ->
        when (val binding = dispatcher.onActivityResult(response)) {
            is UpiResultBinding.Accepted -> pending?.resolve(binding.attempt.toWritableMap())
            is UpiResultBinding.Ignored -> Unit
        }
        pending = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.toAttemptSnapshot()?.let(dispatcher::restore)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAttemptSnapshot(dispatcher.snapshot())
    }

    override fun attachPromise(promise: Promise) {
        pending = promise
    }
}

interface UpiHost {
    val dispatcher: UpiResultDispatcher
    val upiLauncher: ActivityResultLauncher<UpiPaymentRequest>
    fun attachPromise(promise: Promise)
}
```

The native module is named `UpiIntent`. `launchPayment(request): Promise` reads `reactApplicationContext.currentActivity` as `UpiHost` and calls `UpiPayments.launch`. Reject with `INVALID_REQUEST`, `NO_UPI_APP`, `LAUNCH_FAILED`, or `ATTEMPT_IN_PROGRESS`. Resolve a bound client report. Resolve `markReturnedWithoutResult` as `attemptState: "RESULT_UNKNOWN"` and `status: "UNKNOWN"`. An ignored callback does not reject the promise and does not mark the order paid or failed.

```javascript
import { NativeModules, Platform } from "react-native";

const UpiIntent = NativeModules.UpiIntent;

export function launchPayment(request) {
  if (Platform.OS !== "android") {
    return Promise.reject(Object.assign(new Error("UPI intent is Android-only"), { code: "UNSUPPORTED" }));
  }
  return UpiIntent.launchPayment(request);
}

export function markReturnedWithoutResult() {
  if (Platform.OS !== "android") {
    return Promise.reject(Object.assign(new Error("UPI intent is Android-only"), { code: "UNSUPPORTED" }));
  }
  return UpiIntent.markReturnedWithoutResult();
}
```

Disable pay while the attempt is `LAUNCHING` or `WAITING_FOR_RESULT`. The backend, not this promise, decides paid or failed.

Missing until you add them: the package, `UpiIntentModule`, the React package registration, TypeScript types, an example app, and tests.

## Backend, all three hosts

1. Create the order and a unique `transactionRef` on the server. A repeated create returns the same order.
2. The app launches UPI with the values the server returned.
3. The app posts the client report. The server stores it and does not mark the order paid from `reportedStatus`.
4. The server asks the payment provider for that reference and checks amount, currency, and merchant.
5. The app polls or refreshes that server status. `RESULT_UNKNOWN`, a missing callback, `LAUNCH_FAILED`, an ignored callback, and a client `SUCCESS` stay unresolved until the provider check.

Field limits, parser behavior, and illustrative JSON are in [INTEGRATION.md](INTEGRATION.md). The native sample UI is `samples/android-app/src/main/java/com/prabhash/payments/upi/sample/PaymentActivity.kt`.
