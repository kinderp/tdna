# Travel DNA development status

Last updated: 2026-07-17

## Current milestone

**Foundations and Travel DNA Lab v0 — in progress**

## Completed slices

### Java/Rust reference-routing Lab

- issue [#3](https://github.com/kinderp/tdna/issues/3);
- PR [#4](https://github.com/kinderp/tdna/pull/4);
- merged as `d122f1b4871719087e79a50b185ab302d810cb20`.

### Provider-neutral routing contracts and fake planner

- issue [#5](https://github.com/kinderp/tdna/issues/5);
- PR [#6](https://github.com/kinderp/tdna/pull/6);
- merged as `2a28d1654988cef4188986342f76fd7d19be358f`;
- chapter `docs/it/44-contratti-routing-e-fake-provider.md`.

## Active slice

**Provider-neutral MapScene and deterministic fake renderer — implementation and documentation in review**

- issue [#7](https://github.com/kinderp/tdna/issues/7);
- draft PR [#10](https://github.com/kinderp/tdna/pull/10);
- branch `agent/provider-neutral-map-scene`;
- chapter `docs/it/45-map-scene-e-fake-renderer.md`;
- scenario `docs/it/lab/scenarios/map-scene-fake-renderer.md`;
- report `docs/project/daily/2026-07-17-map-scene.md`.

## Implemented foundation capabilities

- Gradle/Kotlin Multiplatform bootstrap;
- JVM and Linux x64 targets;
- generic plugin SDK;
- canonical routing contracts and fake planner;
- provider-neutral `MapScene`, camera, route overlay and marker contracts;
- bounded, immutable scene and marker collections;
- static scene versus frequent delta split;
- canonical route progress `[0, 1)`;
- map renderer capability and error model;
- deterministic fake renderer with semantic snapshot and call history;
- reusable full-capability renderer probe;
- route-to-overlay projector;
- executable map-scene JVM Lab;
- source-level architecture checker that ignores comments/literals;
- three implementation-backed Lab chapters and indexed reports.

## Findings resolved in the active slice

1. architecture checker treated provider names in comments as code dependencies;
2. route progress allowed two equivalent representations through fraction `1.0`;
3. full renderer probe used operations whose capability declarations it did not verify.

All fixes have tests or executable CI evidence.

## Milestone still missing

- `LocationSample` and deterministic clock;
- GPS replay runner;
- missed-exit guidance state machine;
- first benchmark report;
- committed Gradle Wrapper;
- Android/iOS targets;
- real MapLibre or routing provider adapter.

## Next executable step

Complete CI and two clean review rounds for PR #10. After merge, choose between:

1. minimal MapLibre adapter behind `MapRendererPort`;
2. deterministic location/replay slice that produces route-progress deltas.

## Maintainer decisions

No product or architectural decision is currently required. Merge remains with
the maintainer after the review gate is satisfied.
