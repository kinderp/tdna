# Scenario: LocationSample e replay deterministico

id: `lab.location.deterministic-replay.v0`

status: `executable`

scenario kind: `navigation-input-foundation`

## Learning goal

Capire come osservazioni di posizione canoniche vengono validate in ordine,
riprodotte con un clock virtuale e riassunte senza Android, iOS, GPS reale o
wall-clock sleep.

## Prerequisites

- [LocationSample e replay](../../46-location-sample-e-replay-deterministico.md)
- [GPS replay e fixture](../../31-gps-replay-e-fixture.md)
- [Contratti routing](../../44-contratti-routing-e-fake-provider.md)
- [Performance budget](../../32-performance-budget.md)

## User story

Uno studente riproduce uno stream sintetico a doppia velocità. Quattro campioni
vengono accettati; una sequence duplicata e un timestamp monotono arretrato
vengono rifiutati in modo esplicito senza riordinare lo stream.

## Platforms

- Kotlin common core;
- JVM fixture parser e CLI;
- JVM/Linux x64 tests;
- nessun adapter mobile.

## Fixture

- [`reference-location-replay-v0.tdna`](../../../../fixtures/gps/reference-location-replay-v0.tdna)
- [`reference-location-replay-v0.meta.yaml`](../../../../fixtures/gps/reference-location-replay-v0.meta.yaml)

I dati sono sintetici e non derivano da un viaggio personale.

## Trigger

```bash
sh tools/tdna lab location-replay
```

Verifica completa:

```bash
sh tools/tdna check
```

Benchmark diagnostico:

```bash
sh tools/tdna bench location-replay 100000
```

## Input dichiarato

```text
rate 2/1
sample 0, time 0       -> accepted
sample 1, time 1000    -> accepted
sample 1, time 1500    -> rejected: non-increasing sequence
sample 2, time 2000    -> accepted
sample 3, time 1500    -> rejected: non-increasing monotonic time
sample 4, time 3000    -> accepted
```

## Expected evidence

- il formato v0 viene validato;
- nessun sample viene ordinato;
- il primo accepted ha delay zero;
- la duplicate sequence non muove gate o clock;
- il timestamp arretrato non muove gate o clock;
- l'ultimo accepted arriva a `3000 ms`;
- a velocità `2/1` il delay totale è `1500 ms`;
- i motivi di rifiuto hanno conteggio uno ciascuno;
- il runner termina in `Completed`;
- il report coincide con la ground truth.

## Logical tracepoints

```text
LOCATION_REPLAY_FIXTURE_PARSED
LOCATION_SAMPLE_RECEIVED
LOCATION_SAMPLE_ACCEPTED
LOCATION_SAMPLE_REJECTED
REPLAY_CLOCK_BASELINED
REPLAY_CLOCK_ADVANCED
REPLAY_STATE_CHANGED
REPLAY_SUMMARY_EMITTED
```

Sono nomi didattici `stable-doc`, non un output runtime pubblico.

## Function and module path

```text
ReplayFixtureParser.parse()
-> LocationSample(...)
-> LocationReplayScenario(...)
-> DeterministicReplayRunner.runToEnd()
-> LocationSampleGate.evaluate()
-> VirtualReplayClock.reset()/advanceTo()
-> ReplayDelayScaler.scale()
-> ReplaySummary
-> canonicalReplayReport()
```

## State changes

| Stato | Proprietario | Durata |
| --- | --- | --- |
| input list | `LocationReplayScenario` | una esecuzione |
| last accepted sample | `LocationSampleGate` | replay |
| virtual time | `VirtualReplayClock` | replay |
| rate remainder | `ReplayDelayScaler` | replay |
| next index | runner | replay |
| accepted/rejected counters | runner | replay |
| rejection counts | runner | replay |
| final summary | caller | osservazione |

Il runner non conserva la lista degli eventi emessi.

## Expected output

```json
{"scenario":"reference-location-replay-v0","rate":"2/1","state":"completed","processed":6,"accepted":4,"rejected":2,"rejection_counts":{"non_increasing_monotonic_time":1,"non_increasing_sequence":1},"final_time_ms":3000,"playback_delay_ms":1500,"last_sequence":4}
```

## Pause and step variant

Testare:

```text
start
-> advance one sample
-> pause
-> advance rejected because state is Paused
-> step one sample
-> state remains Paused
-> resume
```

Questa variante rende visibile la state machine senza introdurre thread o sleep.

## Nonzero baseline variant

```text
first sample time = 5000
second sample time = 6000
rate = 2/1
```

Atteso:

```text
first source delta = 0
first playback delay = 0
second source delta = 1000
second playback delay = 500
```

## Cancellation variant

Dopo un accepted:

```text
cancel
-> state Cancelled
-> advance rifiutato dallo stato
-> runToEnd rifiutato
-> processed count invariato
```

## Performance properties

- nessun file I/O nel core replay;
- nessun wall-clock sleep;
- nessuna rete;
- stato O(1) rispetto agli eventi emessi, esclusa la lista input;
- scenario massimo 100.000 sample nella v0;
- moltiplicazioni e somme controllate contro overflow;
- benchmark separato dalla correttezza e senza threshold CI.

## Privacy and safety properties

- coordinate sintetiche;
- nessun dato personale;
- nessun permesso mobile;
- nessuna posizione trasmessa;
- nessun uso durante guida reale;
- `LocationSampleOrigin` non è un'autorizzazione di fiducia;
- future fixture reali devono essere anonimizzate e revisionate.

## Existing tests

- `GeoPointTest`;
- `LocationModelsTest`;
- `DeterministicReplayRunnerTest`;
- `ReplayFixtureParserTest`;
- architecture checker;
- Foundation CI;
- CLI ground-truth validation.

## Missing tests

- property test su molte sequenze casuali;
- overflow vicino a `Long.MAX_VALUE`;
- rate modificabile durante pausa;
- streaming fixture invece di lista completa;
- più sorgenti;
- replay concorrente;
- adapter Android/iOS;
- benchmark ripetuto con percentili.

## Future tests

- location replay -> map matcher;
- accepted samples -> route progress;
- progress -> `MapSceneDelta`;
- tunnel e perdita segnale;
- jitter e accuratezza variabile;
- off-route state machine;
- cancellazione coroutine nell'orchestrazione asincrona;
- soak test di ore simulate.

## Common failures

- usare timestamp civile;
- considerare sequence globale;
- ordinare input stantii;
- avanzare clock su rejected;
- calcolare delay iniziale da zero assoluto;
- perdere resto a rate frazionario;
- trattenere eventi senza limite;
- far dormire il test;
- confondere benchmark JVM e batteria mobile;
- importare oggetti location di piattaforma nel common code.

## Non-goals

- GPS reale;
- map matching;
- route progress;
- navigazione turn-by-turn;
- Android/iOS;
- batteria;
- networking;
- dati personali;
- prestazioni di produzione.

## Related docs

- [Capitolo 46](../../46-location-sample-e-replay-deterministico.md)
- [Fixture policy](../../../../fixtures/gps/README.md)
- [Mappa codice e stati](../../40-mappa-codice-e-stati.md)
- [Registro milestone](../../50-registro-milestone.md)
