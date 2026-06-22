# Capsule for Android

The Android client for **Capsule**, a privacy-friendly file-transfer app. Send and receive files (by ID or QR code), with optional on-device end-to-end encryption.

- **Package:** `dev.withcapsule.android`
- **Min SDK:** 29 · **Target SDK:** 36
- Built with Kotlin, Jetpack Compose, Retrofit, CameraX, and DataStore.

## Building

Open the `Android/` directory in Android Studio and run the `app` configuration, or use the wrapper:

```sh
./gradlew installDebug      # build and install on a connected device/emulator
./gradlew bundleRelease     # build a release AAB
```

## Documentation

Full documentation — setup, usage, self-hosting, and API reference — lives at [docs.withcapsule.dev](https://docs.withcapsule.dev).

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for how the app handles data.
