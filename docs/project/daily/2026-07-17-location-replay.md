# Report di sviluppo — 17 luglio 2026 — LocationSample e replay

## Obiettivo

Preparare il primo ingresso deterministico del futuro navigation runtime:

```text
LocationSample canonico
-> gate di ordering
-> clock monotono virtuale
-> replay controllabile
-> report e benchmark diagnostico
```

La slice è sviluppata come PR stacked sopra MapScene per continuare il lavoro
senza modificare le PR già revisionate.

## Tracciabilità

- issue: [#11](https://github.com/kinderp/tdna/issues/11);
- PR draft: [#12](https://github.com/kinderp/tdna/pull/12);
- branch: `agent/location-replay-foundation`;
- dipendenza: PR [#10](https://github.com/kinderp/tdna/pull/10);
- capitolo: `docs/it/46-location-sample-e-replay-deterministico.md`;
- scenario: `docs/it/lab/scenarios/location-replay-deterministico.md`.

## Moduli introdotti

```text
shared/geo-contracts
shared/location-contracts
shared/location-replay
labs/location-replay-cli
fixtures/gps/reference-location-replay-v0.*
```

## Estrazione di GeoPoint

`GeoPoint` era collocato nel bounded context routing. La posizione, la mappa, il
diario e la presenza hanno però bisogno di un contratto geografico comune.

È stato creato:

```text
shared/geo-contracts
```

`routing-contracts` usa il nuovo modulo e conserva un `typealias` temporaneo per
compatibilità sorgente Kotlin interna v0.

## Contratto LocationSample

Campi:

```text
sequence
monotonic time
WGS84 point
horizontal accuracy
optional speed
optional bearing
origin
```

Limiti v0:

```text
accuracy: (0, 100000] m
speed: [0, 200] m/s
bearing: [0, 360)
```

Nessun oggetto Android o iOS entra nel common code.

## Ordering gate

`LocationSampleGate` accetta soltanto:

```text
sequence strettamente crescente
tempo monotono strettamente crescente
```

Rifiuti:

```text
NonIncreasingSequence
NonIncreasingMonotonicTime
```

Il rejected sample non cambia l'ultimo accepted e non viene ordinato.

## Replay engine

Aggiunti:

```text
PlaybackRate razionale
ReplayDelayScaler con remainder
VirtualReplayClock
ReplayState
ReplayEvent
ReplaySummary
DeterministicReplayRunner
```

State machine:

```text
Ready -> Running <-> Paused -> Completed
Ready/Running/Paused -> Cancelled
```

Il runner:

- non legge file;
- non usa rete;
- non dorme;
- non usa wall clock;
- non conserva un event log illimitato;
- espone `advance`, `step`, `pause`, `resume`, `cancel`, `runToEnd`.

## Fixture

Formato:

```text
TDNA_LOCATION_REPLAY_V0
scenario
rate
sample...
expect...
```

La fixture reference contiene sei sample, due deliberatamente invalidi:

- sequence duplicata;
- timestamp monotono arretrato.

Ground truth:

```text
accepted: 4
rejected: 2
final clock: 3000 ms
playback delay at 2x: 1500 ms
last accepted sequence: 4
```

## Finding e correzioni

### Finding 1 — baseline temporale iniziale

La prima versione calcolava il delay del primo sample da zero. Una traccia con
timestamp monotoni assoluti di device uptime avrebbe quindi prodotto un enorme
ritardo iniziale.

Correzione:

```text
first accepted sample
-> clock reset to sample time
-> source delta 0
-> playback delay 0
```

Un test usa timestamp iniziale `5000 ms` e verifica la baseline.

### Finding 2 — expectation parser troppo permissivo

La prima bozza poteva accettare aspettative negative o sample prima della rate.

Correzione:

- header, scenario e rate ordinati;
- sample prima delle expectation;
- valori expected non negativi;
- accepted + rejected uguale al numero di sample;
- rejection reason counts coerenti;
- test per ordering e valori negativi.

## Playback razionale

Il rate usa termini interi bounded e viene ridotto ai minimi termini.

`ReplayDelayScaler` conserva il resto fra intervalli. Esempio `3/2` su tre delta
da 1 ms:

```text
0 ms + 1 ms + 1 ms = 2 ms
```

Questo evita drift dovuto ad arrotondamento per campione.

## CLI e report

Comando:

```bash
sh tools/tdna lab location-replay
```

Report atteso:

```json
{"scenario":"reference-location-replay-v0","rate":"2/1","state":"completed","processed":6,"accepted":4,"rejected":2,"rejection_counts":{"non_increasing_monotonic_time":1,"non_increasing_sequence":1},"final_time_ms":3000,"playback_delay_ms":1500,"last_sequence":4}
```

## Benchmark seed

Comando:

```bash
sh tools/tdna bench location-replay 100000
```

Il benchmark:

- costruisce i sample prima della misura;
- esegue un warm-up;
- misura il solo runner JVM;
- non ha threshold CI;
- non rappresenta GPS, batteria, map matching o UI.

I numeri finali devono essere registrati con ambiente, commit e ripetizioni prima
di guidare decisioni.

## Test

Aggiunti:

- coordinate WGS84 nel nuovo geo module;
- bounds di accuracy, speed e bearing;
- ordering sequence/time;
- rejected state immutabile;
- normalizzazione rate;
- remainder scaling;
- summary accepted/rejected;
- virtual clock invariato sui rifiuti;
- baseline nonzero;
- pause, step e resume;
- cancel;
- snapshot dello scenario;
- parser fixture valido;
- direttiva sconosciuta;
- sample prima della rate;
- expectation negativa;
- mismatch di ground truth.

## Architettura

Il checker ora protegge:

```text
geo-contracts -> Kotlin
location-contracts -> geo-contracts
location-replay -> location-contracts
```

Vieta oggetti location di piattaforma e dipendenze da provider nei moduli common.

## Review plan

La PR è stacked. I due round finali inizieranno soltanto dopo:

1. merge della PR #10;
2. rebase/retarget della PR #12 su `main`;
3. CI verde sulla diff finale;
4. commit del presente report e di tutta la documentazione.

Round 1:

- modelli e bounds;
- ordering;
- state machine;
- clock e rate;
- parser e test.

Round 2:

- dependency direction;
- hot path;
- privacy fixture;
- benchmark semantics;
- documentazione e scope.

## Decisioni autonome

1. estrarre `GeoPoint` in un modulo cross-domain;
2. usare sequence e tempo monotono;
3. non ordinare input;
4. mantenere stato gate O(1);
5. usare rate razionale;
6. non fare sleep nel core;
7. non conservare una timeline eventi illimitata;
8. assegnare baseline al primo accepted;
9. tenere il parser file nel Lab JVM;
10. rendere il benchmark diagnostico e senza threshold.

## Decisioni richieste

Nessuna decisione di prodotto. Il maintainer deve mantenere l'ordine di merge:

```text
PR #9 governance
-> PR #10 MapScene
-> PR #12 location replay
```

## Debito e non-obiettivi

- nessun adapter Android/iOS;
- nessun map matching;
- nessun route progress;
- nessuna coroutine cancellation;
- nessun streaming parser;
- nessuna fixture personale;
- benchmark non ancora ripetuto su hardware dichiarato;
- Gradle Wrapper assente;
- typealias GeoPoint temporaneo.

## Prossimo passo

Dopo il merge, collegare accepted `LocationSample` a una prima interfaccia di
position filtering/map matching oppure usare il replay per alimentare una state
machine di route progress.
