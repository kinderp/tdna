# LocationSample, tempo monotono e replay deterministico

## Stato del capitolo

`implementation-backed — Foundations and Travel DNA Lab v0`

Questo capitolo introduce il primo confine eseguibile per le osservazioni di
posizione. Il codice si trova in:

```text
shared/geo-contracts/
shared/location-contracts/
shared/location-replay/
labs/location-replay-cli/
fixtures/gps/
```

Non esistono ancora adapter Android/iOS, map matching o route progress. La slice
stabilisce prima quali dati entrano nel runtime, come vengono ordinati e come si
riproducono senza dipendere dall'orologio reale.

## Cosa imparerai

Al termine dovresti saper spiegare:

1. perché una posizione non è soltanto latitudine e longitudine;
2. la differenza fra tempo civile e tempo monotono;
3. perché il core non riceve direttamente Android `Location` o iOS `CLLocation`;
4. come sequence e timestamp proteggono l'ordine dello stream;
5. perché campioni rifiutati non vengono ordinati o corretti silenziosamente;
6. quali metadati di sensore vengono bounded;
7. perché il primo campione definisce la baseline del replay;
8. come una velocità razionale evita differenze floating point;
9. come viene preservato il resto dei millisecondi scalati;
10. cosa significano `Ready`, `Running`, `Paused`, `Completed` e `Cancelled`;
11. perché il replay non dorme e non legge file nel core;
12. come una fixture diventa una prova riproducibile;
13. che cosa misura e non misura il microbenchmark;
14. come questa slice prepara map matching e guidance.

## Prerequisiti

- [Glossario](02-glossario.md)
- [Architettura generale](20-architettura-generale.md)
- [Contratti routing e fake provider](44-contratti-routing-e-fake-provider.md)
- [MapScene e fake renderer](45-map-scene-e-fake-renderer.md)
- [GPS replay e fixture](31-gps-replay-e-fixture.md)
- [Performance budget](32-performance-budget.md)

## Perché estrarre `GeoPoint`

La prima slice Kotlin aveva collocato `GeoPoint` nei contratti di routing. Con
l'arrivo della posizione emerge un problema:

```text
location-contracts -> routing-contracts -> GeoPoint
```

Una osservazione GPS non appartiene al routing. Anche mappe, diario, presenza e
media geolocalizzati useranno coordinate.

La struttura diventa:

```text
geo-contracts
    -> routing-contracts
    -> location-contracts
    -> map contracts tramite i contratti già esistenti
```

Il tipo reale vive ora in:

```text
org.traveldna.geo.contracts.GeoPoint
```

`routing-contracts` mantiene temporaneamente un `typealias` per compatibilità
sorgente Kotlin:

```kotlin
typealias GeoPoint = org.traveldna.geo.contracts.GeoPoint
```

Questa è una transizione interna v0, non una promessa di compatibilità binaria.
Il vantaggio architetturale è che i futuri moduli location non dipendono dal
bounded context routing.

## Una posizione non è un punto isolato

Per elaborare uno stream servono almeno:

```text
sequence
monotonic time
WGS84 position
horizontal accuracy
optional speed
optional bearing
origin
```

Il modello v0 è:

```kotlin
LocationSample(
    sequence = LocationSequence(...),
    monotonicTime = MonotonicInstant(...),
    position = GeoPoint(...),
    horizontalAccuracyMeters = ...,
    speedMetersPerSecond = ...,
    bearingDegrees = ...,
    origin = LocationSampleOrigin.Replay,
)
```

Il modello è immutabile. Nessun oggetto piattaforma entra nei moduli condivisi.

## Tempo civile e tempo monotono

### Tempo civile

È il tempo mostrato all'utente:

```text
2026-07-17 14:25:00 Europe/Rome
```

Può cambiare per:

- sincronizzazione NTP;
- modifica manuale;
- timezone;
- ora legale;
- correzioni del sistema.

È adatto al diario e alla presentazione, non a misurare intervalli nel percorso
caldo.

### Tempo monotono

Un orologio monotono serve per:

```text
ordine
intervallo
latenza
scadenza
progressione
```

Non deve tornare indietro durante la vita del processo o della sessione.

Travel DNA usa:

```kotlin
MonotonicInstant(milliseconds: Long)
```

con valori non negativi e confronto esplicito.

### Adapter futuri

Su Android un adapter potrà usare una sorgente basata su elapsed realtime. Su
iOS dovremo definire l'istante monotono di arrivo o un mapping documentato dalla
sorgente piattaforma. La scelta appartiene all'adapter; il core vede soltanto il
contratto canonico.

Non bisogna convertire ciecamente un timestamp civile del provider in tempo
monotono.

## `LocationSequence`

Il timestamp da solo non risolve tutti i casi:

- due campioni possono avere lo stesso tempo per arrotondamento;
- un provider può riconsegnare una osservazione;
- una coda può duplicare un messaggio;
- un test deve distinguere chiaramente gli elementi.

Per questo il flusso ha anche:

```kotlin
LocationSequence(value: Long)
```

La sequenza è locale alla sessione/sorgente canonica e cresce strettamente.
Non è un identificatore globale dell'utente e non deve essere riusata come dato
sociale.

## Metadati bounded

### Accuratezza orizzontale

V0 accetta:

```text
0 < accuracy <= 100000 metri
```

Zero, valori negativi, infiniti o NaN sono impossibili nel contratto. Il limite
alto evita numeri arbitrari pur lasciando rappresentabile una localizzazione
molto scarsa.

Accettare il campione nel modello non significa considerarlo adatto alla
navigazione. Una futura policy potrà rifiutarlo o degradare la confidenza.

### Velocità

Opzionale:

```text
0 <= speed <= 200 m/s
```

Il valore massimo è un limite v0 stradale molto permissivo, non un filtro
antifrode definitivo.

### Bearing

Opzionale:

```text
0 <= bearing < 360 gradi
```

`360` viene normalizzato dall'adapter oppure rifiutato; nel modello canonico
esiste una sola rappresentazione del nord.

### Origine

```text
Platform
Replay
Simulator
```

L'origine aiuta test e diagnostica, ma non sostituisce provenance, permessi o
trust model di produzione.

## Il gate dello stream

`LocationSampleGate` possiede soltanto l'ultimo campione accettato.

Regole:

```text
new.sequence > last.sequence
new.monotonicTime > last.monotonicTime
```

Se una regola fallisce, il gate produce:

```text
NonIncreasingSequence
NonIncreasingMonotonicTime
```

Il campione rifiutato:

- non aggiorna `lastAccepted`;
- non muove l'orologio virtuale;
- non viene ordinato;
- non viene corretto;
- resta osservabile come rifiuto.

### Perché non ordinare automaticamente

Immaginiamo:

```text
seq 10, t=1000
seq 12, t=3000
seq 11, t=2000
```

Ordinare in memoria nasconderebbe un problema di consegna e cambierebbe il
comportamento real-time. Nel runtime stradale il campione `11` arriva davvero
dopo `12`; la policy deve sapere che è stantio.

Il replay conserva quindi l'ordine dichiarato dalla fixture.

## Decisioni del gate

```text
LocationSampleDecision.Accepted
LocationSampleDecision.Rejected
```

Un rifiuto include:

- campione;
- motivo;
- ultima sequence accettata;
- ultimo istante accettato.

Questo rende un test capace di spiegare **perché** lo stream non è avanzato.

## Fixture v0

Il formato line-oriented è:

```text
TDNA_LOCATION_REPLAY_V0
scenario reference-location-replay-v0
rate 2 1
sample 0 0 37.500000 15.100000 5.0 20.0 90.0 replay
...
expect accepted 4
expect rejected 2
expect final_time_ms 3000
expect playback_delay_ms 1500
expect last_sequence 4
expect reason non_increasing_sequence 1
expect reason non_increasing_monotonic_time 1
```

Perché un formato semplice:

- si legge durante una lezione;
- non richiede dipendenze JSON/YAML nel core;
- è versionato;
- ha ground truth esplicita;
- distingue dati e metadati;
- rende facili fixture minime.

Il parser JVM è volutamente fuori da `shared/location-replay`:

```text
file I/O
-> ReplayFixtureParser JVM
-> LocationReplayScenario common
-> DeterministicReplayRunner common
```

Il percorso caldo futuro non leggerà mai righe di testo.

## Playback rate razionale

La velocità è rappresentata da:

```text
numerator / denominator
```

Esempi:

```text
1/2 -> mezza velocità
1/1 -> tempo reale logico
2/1 -> doppia velocità
10/1 -> dieci volte
```

La classe normalizza il rapporto:

```text
2/2 -> 1/1
```

Numeratore e denominatore sono positivi e bounded.

### Perché non `Double`

Con un moltiplicatore floating point, piattaforme diverse potrebbero accumulare
arrotondamenti leggermente differenti. Il replay vuole report deterministici.

Per il delay:

```text
playback delay = source delta * denominator / numerator
```

## Conservare il resto

Con velocità `3/2`, tre intervalli da un millisecondo valgono in totale due
millisecondi di playback:

```text
1 ms -> 0 ms, resto 2
1 ms -> 1 ms, resto 1
1 ms -> 1 ms, resto 0
```

Se arrotondassimo ogni intervallo separatamente senza resto, otterremmo zero e
perderemmo tempo cumulativo.

`ReplayDelayScaler` conserva il resto intero e usa moltiplicazioni controllate
contro overflow.

## Il primo campione come baseline

Un timestamp monotono reale potrebbe essere:

```text
5_432_100 ms dall'avvio del dispositivo
```

Non significa che il replay debba attendere 5.432 secondi prima del primo
campione. Il primo campione accettato stabilisce la baseline:

```text
first source delta = 0
first playback delay = 0
clock = first monotonic timestamp
```

I campioni successivi usano differenze rispetto all'ultimo accettato.

Questa regola è protetta da un test con primo timestamp diverso da zero.

## Orologio virtuale

`VirtualReplayClock`:

- parte da zero come stato vuoto;
- viene resettato al primo timestamp accettato;
- avanza soltanto in avanti;
- restituisce il delta;
- non legge `System.currentTimeMillis`;
- non dorme;
- non dipende dal scheduler del sistema operativo.

La separazione permette di testare ore di viaggio in pochi millisecondi di CPU.

## State machine del runner

```text
Ready
  -> Running
  -> Paused
  -> Running
  -> Completed

Ready/Running/Paused
  -> Cancelled
```

### `start()`

Valido solo da `Ready`.

### `advance()`

Processa un campione solo in `Running`.

### `pause()` e `resume()`

Rendono esplicito il controllo didattico o diagnostico.

### `step()`

Da `Ready` o `Paused` processa un singolo campione e resta in pausa, salvo
completamento.

### `cancel()`

Impedisce ulteriori elaborazioni. Non cancella o riscrive gli eventi già
processati.

### `runToEnd()`

Esegue sincronicamente tutti i campioni restanti senza attese reali.

## Eventi e summary

Per ogni input il runner restituisce:

```text
ReplayEvent.Accepted
ReplayEvent.Rejected
```

Un accepted contiene:

- campione;
- clock corrente;
- delta sorgente;
- delay playback.

Un rejected contiene:

- campione;
- clock invariato;
- motivo.

Il runner **non conserva una lista illimitata degli eventi**. Mantiene:

- indice di input;
- ultimo sample accettato tramite il gate;
- clock;
- contatori;
- conteggi dei motivi;
- delay totale.

`ReplaySummary` è una snapshot bounded.

## Report canonico del Lab

Il comando:

```bash
sh tools/tdna lab location-replay
```

produce una forma come:

```json
{"scenario":"reference-location-replay-v0","rate":"2/1","state":"completed","processed":6,"accepted":4,"rejected":2,"rejection_counts":{"non_increasing_monotonic_time":1,"non_increasing_sequence":1},"final_time_ms":3000,"playback_delay_ms":1500,"last_sequence":4}
```

Il report è stabile per il Lab, ma non è ancora uno schema pubblico.

## Benchmark diagnostico

Comando:

```bash
sh tools/tdna bench location-replay 100000
```

Il runner:

1. costruisce prima la lista sintetica;
2. esegue un passaggio di warm-up JVM;
3. misura soltanto `runToEnd` con `System.nanoTime`;
4. mostra elapsed e nanosecondi per campione.

Il benchmark non ha soglia CI. Un numero è utilizzabile soltanto se accompagnato
da:

```text
commit
CPU/macchina
OS
JVM
Gradle/Kotlin
sample count
build mode
numero di ripetizioni
media/percentili
limiti
```

Non misura:

- GPS piattaforma;
- file parsing;
- map matching;
- UI;
- batteria;
- rete;
- garbage collection di una sessione reale;
- dispositivi mobili.

È un seed metodologico, non una prova di produzione.

## Percorso futuro verso il navigatore

```text
Android/iOS location adapter
-> LocationSample
-> LocationSampleGate
-> position filter
-> map matcher
-> route progress
-> NavigationSnapshot
-> MapSceneDelta.UpdateRouteProgress
```

La slice corrente copre soltanto i primi due passaggi e il replay.

## Errori comuni

- usare tempo civile per gli intervalli del navigation loop;
- ordinare silenziosamente campioni stantii;
- avanzare il clock su un rifiuto;
- trattenere tutta la timeline nel runner;
- considerare sequence un ID globale;
- includere Android/iOS types in common code;
- far dormire il replay nei test;
- calcolare il primo delay da zero assoluto;
- usare `Double` per un rate che deve essere deterministico;
- dichiarare il microbenchmark come prova mobile;
- committare una traccia personale come fixture.

## Esercizi

1. Aggiungere un campione con bearing nullo.
2. Creare due sample con sequence crescente ma timestamp uguale.
3. Verificare che un campione rifiutato non impedisca a un successivo sample
   valido con la stessa sequence proposta di essere accettato.
4. Provare playback `3/2` e spiegare il resto.
5. Avviare, mettere in pausa e usare `step()`.
6. Cancellare a metà scenario e osservare il summary.
7. Creare una fixture con timestamp iniziale `5000000` e verificare delay zero.
8. Disegnare un adapter Android senza importarlo nei contratti.
9. Progettare un filtro di accuratezza separato dal costruttore.
10. Ripetere il benchmark e documentare perché due risultati differiscono.

## Non-obiettivi

- GPS reale;
- permission handling;
- background location;
- filtro Kalman;
- map matching;
- route progress;
- off-route;
- rerouting;
- batteria;
- Android/iOS integration;
- garanzie hard real-time;
- dataset di viaggi personali.

## Collegamenti

- [Scenario Lab location replay](lab/scenarios/location-replay-deterministico.md)
- [Fixture GPS](../../fixtures/gps/README.md)
- [Strategia test](30-strategia-test.md)
- [Mappa codice e stati](40-mappa-codice-e-stati.md)
- [Travel DNA Lab](42-traveldna-lab-roadmap.md)
- [Registro milestone](50-registro-milestone.md)
