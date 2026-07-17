# Travel DNA development status

Last updated: 2026-07-17

## Current milestone

**Foundations and Travel DNA Lab v0 — in progress**

## Completed slices

### Java/Rust reference-routing Lab

- issue [#3](https://github.com/kinderp/tdna/issues/3);
- PR [#4](https://github.com/kinderp/tdna/pull/4);
- merge commit `d122f1b4871719087e79a50b185ab302d810cb20`;
- chapter `docs/it/43-reference-routing-java-rust.md`.

### Provider-neutral routing contracts and deterministic fake planner

- issue [#5](https://github.com/kinderp/tdna/issues/5);
- PR [#6](https://github.com/kinderp/tdna/pull/6);
- merge commit `2a28d1654988cef4188986342f76fd7d19be358f`;
- chapter `docs/it/44-contratti-routing-e-fake-provider.md`;
- scenario `docs/it/lab/scenarios/routing-contracts-fake-provider.md`.

### Governance: two consecutive clean review rounds

- issue [#8](https://github.com/kinderp/tdna/issues/8);
- PR [#9](https://github.com/kinderp/tdna/pull/9);
- merge commit `044e0773dd9afb1530db35688a00c56bfbd5eace`;
- operational rule `docs/it/00-regole-operative.md`;
- detailed chapter `docs/it/06-review-e-merge.md`;
- bounded provider-controlled maneuver metadata with Kotlin regression tests.

## Active slice

**Provider-neutral MapScene and deterministic fake renderer — implementation complete, final CI and two clean reviews pending after reconciliation with `main`**

- issue [#7](https://github.com/kinderp/tdna/issues/7);
- PR [#10](https://github.com/kinderp/tdna/pull/10);
- branch `agent/provider-neutral-map-scene`;
- chapter `docs/it/45-map-scene-e-fake-renderer.md`;
- scenario `docs/it/lab/scenarios/map-scene-fake-renderer.md`;
- report `docs/project/daily/2026-07-17-map-scene.md`.

## Implemented foundation capabilities

- Gradle/Kotlin Multiplatform bootstrap;
- JVM and Linux x64 targets;
- generic plugin SDK;
- bounded runtime platform/capability metadata;
- canonical routing contracts and fake planner;
- provider-neutral `MapScene`, camera, route overlay and marker contracts;
- bounded, immutable scene and marker collections;
- static scene versus frequent delta split;
- canonical route progress `[0, 1)`;
- map renderer capability and error model;
- deterministic fake renderer with semantic snapshot and call history;
- reusable full-capability renderer probe;
- route-to-overlay projector;
- executable Java/Rust, routing-contract and map-scene Labs;
- source-level architecture checker that ignores comments/literals;
- mandatory two-clean-review governance;
- indexed daily reports.

## Findings resolved in the active slice

1. architecture checker treated provider names in comments as code dependencies;
2. route progress allowed two equivalent representations through fraction `1.0`;
3. full renderer probe used operations whose capability declarations it did not verify;
4. `UnsupportedOperation` could be marked retryable without a provider change;
5. six duplicate contract files were added after the reviewed head and were removed by restoring the reviewed tree before reconciliation.

All product-code findings have tests or executable CI evidence. The current documentation-only reconciliation with merged PR #9 resets the final review sequence as required.

## Milestone still missing

- `LocationSample` and deterministic clock;
- GPS replay runner;
- missed-exit guidance state machine;
- first benchmark report;
- committed Gradle Wrapper;
- Android/iOS targets;
- real MapLibre or routing provider adapter.

## Next executable step

Complete CI and two clean review rounds for PR #10. After merge, continue issue
[#11](https://github.com/kinderp/tdna/issues/11): canonical `LocationSample`,
virtual monotonic clock and deterministic replay.

## Maintainer decisions

No product or architectural decision is currently required. Merge remains with
the maintainer after CI and two consecutive clean review rounds on the current
substantive head.
