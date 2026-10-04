# UPI Intent Wrapper for Android

Kotlin Android library scaffold to launch compatible UPI apps and parse best-effort responses.

> **Not a payment gateway or certified payment solution.** Client callbacks are untrusted and do not prove funds were received. Production commerce must verify final status server-side through the merchant's acquiring bank or an authorized payment provider.

## Features
- Typed request and basic validation
- UPI URI creation
- AndroidX Activity Result contract
- Defensive response parsing (`PENDING`, `UNKNOWN`, `CANCELLED_OR_UNKNOWN`)
- Unit tests and integration guide

## Requirements
- Android min SDK 23; compile SDK 35
- Java 17; Kotlin

## Usage
See [docs/INTEGRATION.md](docs/INTEGRATION.md).

UPI apps differ in callback behavior. The initiating app cannot force another app to close or guarantee every app returns a callback. A `SUCCESS` parsed from a client response is only a reported status, not independent verification.
