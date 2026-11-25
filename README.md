# we-zte-router-control

Android app for a ZTE / WE fiber router (admin UI at `https://192.168.1.1`).

## What it does

- Opens the router admin page in an embedded browser. You sign in there yourself.
- Reads the connected-device table from the signed-in page and shows name, MAC and IP in a native list.
- Tap a device to copy its MAC and jump to the router's MAC-filter page when the app can find one; otherwise it opens the router UI and tells you where to block manually.
- A diagnostics screen probes the usual ZTE/ZXHN filter paths, reports the HTTP status, the page title, and whether a MAC table or a MAC-filter form was actually found.

## What it does not do

- It does not store router credentials. No password is written anywhere; the router session lives in the embedded browser only.
- It does not claim to block a device on its own. A one-tap block that writes to the router is only allowed once the exact write endpoint and its success confirmation are known for the firmware in use. With unknown firmware the block action is a guided manual step.
- The MAC parsing is best effort: a few firmwares show malformed MAC strings (missing octets), which will not be listed as a MAC.

## Privacy

- No analytics, no network calls except to the router address you type.
- The repository contains no credentials, no device MACs, and no personal identifiers. Don't commit screenshots that show real MAC addresses or SSIDs.

## Build

A debug APK is built by GitHub Actions (`.github/workflows/android.yml`) on every push to `main` and on manual dispatch, and published as a release asset named `app-debug.apk`.

Locally: `gradle assembleDebug` (JDK 17, Android SDK 34). No wrapper is committed; install Gradle 8.9 or use the workflow.

## Testing

Tested only to the point of an installed build on the owner's router. Real device listing and any router-side block must be verified on the router itself, because the model and firmware version are not known.
