# Travel DNA development status

Last updated: 2026-07-17

## Current milestone

**Foundations and Travel DNA Lab v0 — in progress**

## Completed slices

| Slice | Issue / PR | Merge |
| --- | --- | --- |
| Java/Rust reference routing | #3 / #4 | `d122f1b4871719087e79a50b185ab302d810cb20` |
| Provider-neutral routing contracts | #5 / #6 | `2a28d1654988cef4188986342f76fd7d19be358f` |
| Two-clean-review governance | #8 / #9 | `044e0773dd9afb1530db35688a00c56bfbd5eace` |
| MapScene and fake renderer | #7 / #10 | `2128f24b4a6ab00aeb437ed48a0af2910f33e9ec` |
| Serial PR governance | #14 / #15 | `76680433089842db5805d28eb50416a23c7d0a88` |
| LocationSample and deterministic replay | #11 / #16 | `020f8495f7fbbae81f1463b098b0ddd2a079c873` |

## Active slice

**Canonical matched position and route progress tracker**

- issue [#17](https://github.com/kinderp/tdna/issues/17);
- PR [#18](https://github.com/kinderp/tdna/pull/18);
- branch `agent/matched-route-progress`;
- base `020f8495f7fbbae81f1463b098b0ddd2a079c873`;
- risk `R2`;
- chapter `docs/it/47-posizione-matched-e-route-progress.md`;
- scenario `docs/it/lab/scenarios/route-progress-tracker.md`;
- report `docs/project/daily/2026-07-17-route-progress.md`.

## Implemented in the active slice

- canonical `RouteCoordinate` and `MatchedRoutePosition`;
- independent sequence/time/route-progress checks;
- stationary updates accepted and regressions rejected without mutation;
- deterministic active-leg boundary policy;
- binary-search leg and maneuver lookup;
- explicit `Arrive` tie-break at the final geometry point;
- public snapshot invariants;
- route-overlay binding with full geometry verification at install time;
- O(1) compact map progress projection;
- common/JVM/Linux tests;
- deterministic Lab report;
- multi-leg diagnostic benchmark with preprocessing outside the timer;
- chapter, scenario and indexed pre-final report.

## Findings resolved before final review

1. linear leg/maneuver scans in a future hot path;
2. same-ID overlay with different geometry not rejected;
3. benchmark route without meaningful legs/maneuvers;
4. tracker preprocessing included in the measured window;
5. boundary leg/maneuver absent from Lab evidence;
6. non-Arrive maneuver could be exposed after arrival;
7. projector test omitted same-length altered geometry;
8. intermediate Kotlin named-argument error caught by CI;
9. generic tracepoint incorrectly claimed distance updates.

Every substantive fix reset the clean-review counter.

## Current gates

- sole open PR: yes;
- code and primary teaching documents: complete;
- stable indexes/status: being finalized on the current branch;
- CI on final substantive head: pending;
- unresolved threads: none observed before final review;
- clean review rounds: `0 / 2`;
- merge: not yet authorized by evidence until all gates pass.

## Milestone still missing after this slice

- real map matching or a separate fake map-matcher contract;
- missed-exit/off-route/reroute state machine;
- Gradle Wrapper;
- Android/iOS targets;
- real MapLibre/routing provider adapter.

## Maintainer decisions

None. Standing authorization permits autonomous merge only after the documented
gates.
