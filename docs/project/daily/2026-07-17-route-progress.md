# Report di sviluppo — 17 luglio 2026 — Matched position e route progress

## Stato del report

`pre-final-review — ledger autorevole nella PR #18`

Questo report è committato prima dei due round finali, come richiesto dalle
[regole operative](../../it/00-regole-operative.md). La timeline della
[PR #18](https://github.com/kinderp/tdna/pull/18) registrerà CI finale, review e
merge senza richiedere un commit successivo che invaliderebbe lo SHA revisionato.

## Obiettivo

Costruire la quinta vertical slice eseguibile della fondazione:

```text
MatchedRoutePosition
-> RouteProgressTracker
-> active leg / upcoming maneuver / arrived
-> RouteProgressMapBinding
-> MapSceneDelta.UpdateRouteProgress
```

Il map matching reale resta fuori scope.

## Tracciabilità

- issue: [#17](https://github.com/kinderp/tdna/issues/17);
- PR: [#18](https://github.com/kinderp/tdna/pull/18);
- branch: `agent/matched-route-progress`;
- base verificata: `020f8495f7fbbae81f1463b098b0ddd2a079c873`;
- capitolo: `docs/it/47-posizione-matched-e-route-progress.md`;
- scenario: `docs/it/lab/scenarios/route-progress-tracker.md`.

## Codice introdotto

### `shared/navigation-contracts`

- `RouteCoordinate` canonica con indice e frazione `[0, 1)`;
- `MatchedRoutePosition` con route ID, sequence, tempo, lateral distance e confidence;
- `RouteProgressSnapshot`, decisioni e motivi di rifiuto;
- cursore manovra con leg/maneuver index;
- invarianti sugli snapshot arrived e sulle leg completate.

### `shared/route-progress`

- tracker single-owner legato a una `RoutePlan`;
- sequence, tempo e progresso controllati separatamente;
- coordinate uguali accepted per il veicolo fermo;
- regressioni rifiutate senza mutazione;
- leg lookup e maneuver lookup con binary search;
- tie-break della nuova leg al confine;
- `Arrive` finale pre-selezionata;
- `inspect`, `accept` e `reset`.

### `shared/route-progress-map-projector`

- binding route-overlay verificato all'installazione;
- route ID e geometria confrontati una sola volta;
- update per campione `O(1)` con ID, indice e frazione;
- nessuna copia o ricostruzione della geometria nel loop.

### `labs/route-progress-cli`

- route sintetica a due leg;
- sei posizioni matched;
- una regressione intenzionale;
- ground truth sul confine e sull'arrivo;
- report JSON deterministico;
- benchmark multi-leg/manovre.

## Finding e correzioni

### Finding 1 — scansioni lineari nel percorso caldo

La prima versione cercava leg e manovra attraversando liste complete.

Correzione:

- binary search sugli `geometryEndIndex` delle leg;
- binary search sui cursori manovra;
- skip locale soltanto per duplicati al confine.

### Finding 2 — overlay stesso ID ma geometria errata

Il projector controllava route ID e indice, ma un overlay stale con lo stesso ID
e geometria diversa poteva ricevere un delta semanticamente scorretto.

Correzione:

- `RouteProgressMapProjector.bind(scene, overlay, route)` confronta route ID e
  geometria durante l'installazione;
- restituisce un binding non forgiabile liberamente;
- `project(binding, snapshot)` resta `O(1)` per campione.

### Finding 3 — benchmark iniziale poco rappresentativo

La route benchmark iniziale aveva una sola leg e nessuna manovra.

Correzione:

- fino a 100 leg;
- una manovra per leg più `Arrive`;
- 10.000 posizioni;
- output con numero di leg e manovre.

### Finding 4 — preprocessing incluso nella finestra misurata

Il benchmark costruiva il tracker dentro ogni iterazione, includendo la
preparazione dei cursori pur dichiarando di osservare il percorso caldo.

Correzione:

- route, posizioni e tracker costruiti prima dei warm-up;
- `reset` eseguito fuori dal timer;
- finestra misurata limitata agli `accept`, ricerche, snapshot e commit.

### Finding 5 — ground truth del confine non osservabile

Il primo report non mostrava quale leg/manovra fossero state scelte sul punto
condiviso.

Correzione:

```text
boundary_leg = 1
boundary_maneuver = Continue
```

### Finding 6 — arrival con manovra ambigua

Con più manovre sul punto finale, la ricerca generica poteva esporre una manovra
non-arrival in uno snapshot `arrived`.

Correzione:

- `Arrive` finale pre-selezionata una volta;
- snapshot arrived limita la manovra a `Arrive` o `null`;
- regression test con `KeepRight` e `Arrive` allo stesso indice.

### Finding 7 — contratto projector e test incompleti

La verifica copriva overlay troncati ma non geometrie diverse della stessa
lunghezza.

Correzione:

- test same-ID/same-length/different-geometry;
- binding verificato prima del loop;
- dipendenze e architecture checker aggiornati.

### Finding 8 — errore di named argument Kotlin

Una versione intermedia usava `position` invece di `location` per
`RouteManeuver`. La CI ha rifiutato il codice e il nome è stato corretto.

### Finding 9 — tracepoint fuorviante sulle distanze

La documentazione precedente diceva che `ROUTE_PROGRESS_UPDATED` aggiornava
anche distanze, ma questa slice non calcola distanza o ETA.

Correzione prevista nello SHA finale: il tracepoint descrive soltanto coordinata,
leg, manovra e arrival.

Ogni finding e commit sostanziale azzera il contatore delle review pulite.

## Test

Copertura principale:

- coordinate e zero firmato;
- lateral distance bounded;
- route mismatch e indici invalidi;
- sequence/tempo non crescenti;
- regressione di indice e frazione;
- stationary update accepted;
- nessuna mutazione dopo rifiuto;
- leg boundary e manovra della nuova leg;
- tie-break `Arrive`;
- `inspect` non mutante e `reset`;
- binding route-overlay e geometria differente;
- projector update;
- benchmark arguments;
- common/JVM/Linux x64;
- architecture boundaries;
- Foundation CI.

## Lab

```bash
sh tools/tdna lab route-progress
```

Output atteso:

```json
{"scenario":"reference-route-progress-v0","accepted":5,"rejected":1,"rejection":"RegressedAlongRoute","boundary_leg":1,"boundary_maneuver":"Continue","completed_index":3,"fraction":0.0,"active_leg":1,"upcoming_maneuver":"Arrive","arrived":true,"delta_index":3}
```

## Benchmark

```bash
sh tools/tdna bench route-progress 10000 7
```

Configurazione:

```text
10.000 geometry points / positions
fino a 100 leg
una manovra per leg + Arrive
3 warm-up
7 run dispari
Ubuntu 24.04 CI
Java 21
Gradle 9.5.1
Kotlin 2.4.0
```

La misurazione finale sullo SHA conclusivo sarà registrata nell'artifact CI e
nel ledger della PR. Non è una soglia e non misura GPS, map matching, renderer,
mobile, batteria o affidabilità stradale.

## Documentazione

- capitolo 47 implementation-backed;
- scenario Lab route progress;
- README e percorsi di lettura;
- stato funzionalità/documentazione/commenti;
- mappa del codice e tracepoint;
- roadmap Lab e milestone;
- development status e questo report indicizzato.

## Review plan

### Round 1

Focus:

- contratti;
- monotonicità;
- precedenza rifiuti;
- leg e maneuver policy;
- snapshot invariants;
- binding;
- state mutation;
- test.

### Round 2

Focus:

- architettura e dipendenze;
- forma del hot path;
- privacy e provenance;
- benchmark e affermazioni;
- documentazione;
- CI e scope milestone.

Servono due round consecutivi sullo stesso substantive head finale.

## Decisioni richieste

Nessuna. L'autorizzazione permanente consente il merge autonomo soltanto dopo
tutti i gate.

## Debito e non-obiettivi

- nessun vero map matcher;
- nessun filtro GPS;
- nessuna distanza o ETA;
- nessun off-route/reroute;
- nessun adapter Android/iOS;
- nessun MapLibre;
- nessun test concorrente;
- nessuna promessa prestazionale di produzione;
- Gradle Wrapper ancora assente.

## Prossimo passo

Completare gli indici, ottenere CI verde sul final substantive head, svolgere i
due round puliti, mergiare con expected-head guard e verificare il nuovo `main`.
La slice successiva potrà introdurre la state machine missed-exit/off-route oppure
un fake map matcher, ma soltanto dopo il merge e il riallineamento.
