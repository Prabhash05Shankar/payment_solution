# UPI Intent Wrapper for Android

Kotlin Android library that builds a validated `upi://pay` intent, launches an installed UPI app, and parses the best-effort text that app returns.

This is not a payment gateway and it is not a certified payment product. A parsed `SUCCESS` only means the UPI app reported success. It does not prove that money was received. The merchant backend must verify the order with the merchant's acquiring bank or authorized payment provider before fulfilling it.

The library does not force another app to close, and it cannot make every UPI app return a result.

## What the library does

- Validates the payee UPI ID, payee name, amount, currency, transaction reference, and optional note.
- Builds an `upi://pay` URI with percent-encoding. The `@` in the payee address is left as-is because UPI apps expect that.
- Finds installed apps that handle `upi://pay` through Android package visibility. It does not keep a list of Google Pay, PhonePe, Paytm, or any other package name.
- Launches the only matching app directly, or the system chooser when more than one matches.
- Parses client responses defensively, including differing key case, URL encoding, duplicate keys, and missing fields.
- Tracks one payment attempt at a time. A second launch is rejected while the first is opening or waiting. A late callback is not applied to a newer attempt unless the payload's transaction reference matches that attempt.
- Exposes stable error codes: `INVALID_REQUEST`, `NO_UPI_APP`, and `LAUNCH_FAILED`. A rejected overlapping launch is `UpiAttemptStart.Rejected`, not a payment failure.

## What it does not guarantee

- That a UPI app is installed, returns to your app, or uses a particular response format.
- That `UpiPaymentStatus.SUCCESS` means the payment is settled. `UpiResponse.isAuthoritativelyVerified` is always false.
- That a missing callback means the payer cancelled or the payment failed. The parsed status is `UNKNOWN`. The attempt state for an explicit "returned without a result" action is `RESULT_UNKNOWN`. Neither one is a failed payment.
- That a UPI response can always be tied to a specific launch. Android does not return this library's attempt id. Matching is reliable only while a single attempt is outstanding, or when the UPI app echoes the same `txnRef`.
- That the system chooser will forward the UPI app's result. Some Android versions drop it.
- Server-side verification. There is no backend in this repository, and the library has no bank credentials.
- Flutter or React Native packages. Those integrations are specified in [docs/INTEGRATION.md](docs/INTEGRATION.md) and are not implemented here.
- A published Maven artifact. Nothing in this repository is published to Maven Central or Google Maven.

## Requirements

- Android `minSdk` 23, `compileSdk` 35
- JDK 17 or 21 to run Gradle. JDK 25 cannot run the Gradle version this project uses.
- Kotlin 2.0.21 and Android Gradle Plugin 8.7.3
- Gradle 8.10.2, supplied by the wrapper

Android Gradle Plugin 8.7.3 requires Gradle 8.9 or newer. Kotlin 2.0.21 is supported through the Gradle 8.10 line. Gradle 9 is outside that Kotlin range, and Android Gradle Plugin 8.7 hits Gradle 9 deprecations that Gradle 10 removes. The wrapper is therefore pinned to Gradle 8.10.2.

The library manifest contributes a `<queries>` element for `upi://pay`. The manifest merger adds it to the host app. The library asks for no permissions.

## Install

Use a project dependency from this repository. There are no published coordinates to add.

```kotlin
dependencies {
    implementation(project(":upi-intent-wrapper"))
}
```

The module exposes AndroidX Activity as an `api` dependency, because `UpiPaymentContract` extends `ActivityResultContract`.

`samples/android-app` is a minimal host that compiles against this API. It is not a payment backend.

## Payment request

```kotlin
val request = UpiPaymentRequest(
    payeeAddress = "merchant@upi",
    payeeName = "Example Merchant",
    amount = "10.00",
    transactionRef = "ORDER-123",
    transactionNote = "Order payment"
)
val errors = request.validate()
```

`amount` uses `.` as the decimal separator and allows at most two decimal places. `0`, negative values, `10,50`, and `10.999` are rejected. `transactionRef` is required and is limited to 35 characters from letters, digits, `.`, `_`, and `-`. A production app should use a reference created by its backend. See [docs/INTEGRATION.md](docs/INTEGRATION.md) for the field limits and the launch flow.

## Tests

From the repository root, on JDK 17 or 21:

```powershell
.\gradlew.bat test
```

```bash
./gradlew test
```

The unit tests cover request validation, URI encoding, response parsing, missing UPI apps, launch-failure classification, attempt states, rejected overlapping launches, duplicate callbacks, stale and uncorrelated results, and restoring an attempt after recreation. They do not place a real UPI payment. Device behavior of Google Pay, PhonePe, Paytm, and other apps was not tested in this environment.

## Build

```powershell
.\gradlew.bat --version
.\gradlew.bat clean
.\gradlew.bat test
.\gradlew.bat assemble
```

`local.properties` is machine-specific and is not part of the project sources. Point the Android SDK at it locally, or set `ANDROID_HOME`. Do not commit keystores or signing passwords. Release builds of the library do not embed a local SDK path.

## Limitations

- UPI intent parameters other than `pa`, `pn`, `am`, `cu`, `tr`, and optional `tn` are not sent. Merchant category, terminal id, and signed-intent fields are omitted on purpose.
- iOS cannot use this Android intent. A Flutter or React Native host still needs a separate iOS design, which this repository does not provide.
- Client responses can omit `txnId`, `txnRef`, `ApprovalRefNo`, and `responseCode`. The parser keeps whatever is present and leaves the rest null. After an unresolved attempt, a newer attempt ignores a callback that does not echo its `txnRef`.
- The sample keeps Pay disabled until the open attempt receives a result, fails to launch, or the user taps "I returned without a result". Coming back to the Activity does not clear that attempt.
