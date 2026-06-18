# Capsule Android

This is the Android client for Capsule. It provides a mobile interface for sending files, receiving files by ID or QR code, and working with locally encrypted transfers.

## Features

- upload files to a Capsule server
- download files by ID or scanned QR code
- optional client-side encryption and decryption
- recent transfer history
- configurable server address for self-hosting

## Project details

- package name: `com.sean.capsule`
- minimum Android version: API 29
- built with Kotlin, Jetpack Compose, Retrofit, CameraX, and DataStore

## Open in Android Studio

Open the `Android/` directory as a Gradle project, then run the `app` configuration on a device or emulator.

Useful commands from this directory:

```sh
./gradlew assembleDebug
./gradlew installDebug
```

## Release signing

The release build reads signing configuration from environment variables:

- `KEYSTORE_PATH`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

If those are not set, the project falls back to the debug signing config.

## Notes

- the app can be pointed at a self-hosted server from settings
- server validation includes a ping check before saving the address
- encrypted transfers require keeping the generated passphrase or mnemonic on the client side
