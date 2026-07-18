# Project tooling

Entry point:

```bash
sh tools/tdna COMMAND
```

## Core commands

| Command | Purpose |
| --- | --- |
| `doctor` | Java/Python/Rust/Wrapper/Android SDK/global Gradle status. |
| `check-gradle-wrapper` | Wrapper version and checksum policy. |
| `check-ci-actions` | Immutable reviewed GitHub Action SHAs. |
| `check-docs` | Markdown links and fences. |
| `check-architecture` | Shared Kotlin source boundaries. |
| `check-java` | Java routing. |
| `check-rust` | Rust routing. |
| `check-contract` | Java/Rust byte report equality. |
| `check-kotlin` | Foundation KMP tests/Labs without Android SDK. |
| `check-android` | Android unit, lint, debug APK and instrumentation APK build. |
| `check` | Complete non-Android foundation verification. |

## Android

Prerequisites:

```text
Java 21
Android SDK Platform 37
Build Tools 36.0.0
```

```bash
sh tools/tdna check-android
```

Outputs:

```text
build/android/tdna-pilot0-debug.apk
build/android/tdna-pilot0-debug-androidTest.apk
build/android/sha256.txt
```

The CI compiles the instrumentation APK but does not yet execute it on an
emulator. A green build is not road evidence.

## Foundation-only Gradle graph

`check-kotlin`, Labs and benchmarks pass:

```text
-Ptdna.includeAndroid=false
```

so deterministic shared work remains accessible without installing the Android
SDK. Android commands include the app by default.

## Generated outputs

All generated artifacts stay under `build/` or module build directories. Never
commit APKs, traces, Gradle caches or real user locations.

## Rules

- local and CI use the same project commands;
- missing required tools fail explicitly;
- new commands require documentation and CI in the same PR;
- benchmarks are observations, not SLA;
- Android device/field evidence is recorded separately from JVM/CI evidence.
