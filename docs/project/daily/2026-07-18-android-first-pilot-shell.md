# Daily development report — 2026-07-18 — Android-first Pilot 0

## Goal

Start the first mobile product milestone after Foundations v0: record the
Android-first decision, publish the pilot/study/field-test roadmap and build a
permission-free installable Jetpack Compose teaching shell.

## Tracking

- issue: [#25](https://github.com/kinderp/tdna/issues/25);
- pull request: [#26](https://github.com/kinderp/tdna/pull/26);
- branch: `agent/android-first-pilot-shell`;
- verified base: `7090882b40e747a85d812decb0b3567a1276d701`;
- risk: `R2`;
- final review/merge ledger: PR #26.

## Decision

Android-first was selected by the maintainer on 2026-07-18 and recorded in
`ADR-0010`.

## Implemented so far

### Build and module boundary

- Android application subproject `apps/android`;
- Android Gradle Plugin 9.3.0;
- Gradle Wrapper 9.5.1;
- compile/target SDK 37, min SDK 26;
- AGP built-in Kotlin for the app;
- Kotlin 2.4.0 Compose compiler plugin;
- Compose BOM 2026.06.00 and Material 3;
- Google Maven repository;
- conditional module include so foundation Labs can run without Android SDK;
- Android SDK 37 installation and APK artifact collection in CI.

### Application

- single `MainActivity`;
- light/dark Compose theme;
- bounded four-screen navigation state;
- Home, pilot roadmap, deterministic demo and study screens;
- real construction of `LocationSample`, `RouteCoordinate` and `OffRoutePolicy`
  from shared contracts;
- no permission, network, storage, service or map SDK;
- unit tests for pilot ordering/non-goals/demo ground truth;
- instrumentation Compose smoke source.

### Tooling

- `sh tools/tdna check-android`;
- debug and instrumentation APK copied to `build/android`;
- SHA-256 manifest;
- CI upload of APKs and observations.

### Documentation

- ADR-0010;
- Android-first pilot roadmap;
- study path using official Android, Pluralsight and Manning material;
- road-pilot protocol;
- Android module README;
- this indexed daily report.

## Pilot windows

```text
Pilot 0 teaching shell     2026-08-10 .. 2026-08-21
Pilot 1 road companion     2026-09-21 .. 2026-10-09
Pilot 2 closed beta        2026-11-02 .. 2026-12-11, re-estimated after Pilot 1
```

These are planning windows, not production commitments.

## Purchases

No additional course or book is required. Manning and Pluralsight subscriptions
plus official Android training cover the initial learning path. A physical
Android phone is the first useful purchase only if the maintainer has no suitable
device before Pilot 1.

## Verification state

The first Android CI run is development evidence only. Documentation and source
may still change, so the final substantive head and clean-review counter have not
been fixed.

## Known risks to validate

- AGP 9.3 / Kotlin 2.4 Compose plugin integration;
- Android consumer resolution of current KMP JVM variants;
- SDK 37 availability on hosted CI;
- Android lint on permission-free shell;
- instrumentation APK compilation;
- current GitHub Actions duration after adding Android build.

## Non-goals

- real GPS;
- foreground service;
- external-navigation Intent;
- MapLibre or routing provider;
- Room/Hilt/Firebase;
- signed/public distribution;
- road reliability.

## Next executable step

Obtain Android CI evidence, correct build or architecture findings, complete all
indexes/status records, then establish the final substantive head for two clean
review rounds.
