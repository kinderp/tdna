# Posizione matched e avanzamento canonico sulla route

## Stato del capitolo

`implementation-backed — Foundations and Travel DNA Lab v0`

Questo capitolo introduce il confine fra un futuro componente di map matching e
il runtime che segue l'avanzamento lungo una `RoutePlan` già nota.

Il codice si trova in:

```text
shared/navigation-contracts/
shared/route-progress/
shared/route-progress-map-projector/
labs/route-progress-cli/
fixtures/navigation/
```

La slice **non** decide su quale strada si trovi un punto GPS. Riceve una
posizione che un componente precedente ha già associato alla geometria della
route e verifica ordine, coerenza e progresso.

## Cosa imparerai

Al termine dovresti saper spiegare:

1. la differenza fra posizione raw, filtrata, matched e route progress;
2. perché `MatchedRoutePosition` non è un algoritmo di map matching;
3. come indice e frazione descrivono un punto lungo una polilinea;
4. perché route ID, sequence, tempo e coordinata hanno invarianti separate;
5. perché un veicolo fermo può produrre coordinate route uguali;
6. perché una regressione non viene corretta silenziosamente;
7. come si sceglie la leg attiva a un confine condiviso;
8. come si seleziona la prossima manovra senza scansioni lineari;
9. che cosa significa `arrived` nella v0;
10. perché indice geometrico e distanza stradale non sono sinonimi;
11. come uno snapshot diventa un delta compatto per la mappa;
12. quali stati possiede il tracker;
13. che cosa misura il benchmark;
14. come questa slice prepara off-route e rerouting senza implementarli.

## Prerequisiti

- [Routing e navigazione](24-routing-e-navigazione.md)
- [Contratti routing e fake provider](44-contratti-routing-e-fake-provider.md)
- [MapScene e fake renderer](45-map-scene-e-fake-renderer.md)
- [LocationSample e replay](46-location-sample-e-replay-deterministico.md)
- [Performance budget](32-performance-budget.md)

## Quattro rappresentazioni diverse

### Posizione raw

È l'osservazione del sensore o del provider:

```text
latitudine
longitudine
accuratezza
velocità
bearing
tempo
```

Travel DNA la normalizza in `LocationSample`.

### Posizione filtrata

Un futuro filtro ridurrà rumore, spike e valori non plausibili. La slice
corrente non contiene ancora questo passaggio.

### Posizione matched

Un map matcher confronta la posizione con strade o route candidate e produce
una ipotesi:

```text
questa osservazione appartiene alla route R
fra i punti geometrici 42 e 43
al 35% del segmento
```

Questa è `MatchedRoutePosition`.

### Route progress

Il tracker confronta la nuova ipotesi con la route e con l'ultima posizione
accettata:

```text
route corretta?
indice valido?
sequence crescente?
tempo crescente?
coordinata non regressiva?
quale leg è attiva?
quale manovra viene dopo?
siamo arrivati?
```

Non deve ripetere il map matching.

## `RouteCoordinate`

```kotlin
RouteCoordinate(
    completedGeometryIndex = 42,
    fractionToNext = 0.35,
)
```

Significa:

- il punto `42` è l'ultimo punto raggiunto della geometria;
- la posizione è al 35% del segmento verso il punto `43`.

Invarianti:

```text
index >= 0
0 <= fraction < 1
fraction finita
```

`1.0` è escluso perché duplichererebbe:

```text
index 42, fraction 1.0
index 43, fraction 0.0
```

Lo zero firmato viene normalizzato:

```text
-0.0 == 0.0
```

La forma canonica rende deterministici confronto, hash, test e cache.

## `MatchedRoutePosition`

Il contratto contiene:

```text
routeId
sampleSequence
monotonicTime
RouteCoordinate
lateralDistanceMeters
confidence
```

### Route ID

Collega la posizione a una route canonica precisa. Un tracker legato alla route
A rifiuta una posizione per la route B.

Il contratto assume che route ID differenti identifichino versioni differenti
della geometria. Riutilizzare lo stesso ID per una nuova route renderebbe
ambigua la cache e non è consentito dal processo applicativo.

### Sequence e tempo

Derivano dallo stream di posizione. Devono crescere entrambi rispetto
all'ultima posizione accepted.

### Distanza laterale

```text
0 <= lateralDistanceMeters <= 100000
```

È metadato del matcher, non una decisione off-route. Il tracker v0 non stabilisce
una soglia di deviazione.

### Confidenza

```text
Low
Medium
High
```

È una classificazione iniziale e non una probabilità universale. Provider
diversi possono calcolare score incompatibili; l'adapter o una policy di
matching li tradurrà nel vocabolario Travel DNA.

Il tracker non rifiuta automaticamente `Low`: una futura policy può decidere se
sospendere guidance, degradare suggerimenti o attendere altri campioni.

## Tre monotonicità separate

### Sequence

```text
new.sequence > previous.sequence
```

Protegge da duplicati e consegne stantie.

### Tempo monotono

```text
new.time > previous.time
```

Protegge l'ordine temporale indipendentemente dalla sequence.

### Coordinata route

```text
new.coordinate >= previous.coordinate
```

Qui l'uguaglianza è valida. Un veicolo fermo può ricevere campioni nuovi con
stessa posizione matched.

Una coordinata minore viene rifiutata come:

```text
RegressedAlongRoute
```

La v0 non introduce una tolleranza di regressione. Rumore e isteresi
appartengono al matcher o a una futura policy esplicita, non a una correzione
nascosta nel tracker.

## Ordine dei controlli

Il tracker usa una precedenza deterministica:

```text
1. RouteMismatch
2. GeometryIndexOutOfBounds
3. FinalPointHasFraction
4. NonIncreasingSequence
5. NonIncreasingMonotonicTime
6. RegressedAlongRoute
```

Un input può violare più regole; la precedenza rende stabile il motivo
osservabile e semplifica test e diagnostica.

## `inspect`, `accept` e `reset`

```kotlin
val decision = tracker.inspect(position)
```

`inspect` non modifica lo stato.

```kotlin
val decision = tracker.accept(position)
```

`accept` salva lo snapshot soltanto quando la decisione è `Accepted`.

```kotlin
tracker.reset()
```

`reset` cancella il progresso della stessa route e permette una nuova sessione.
Una route sostitutiva usa un nuovo `RouteProgressTracker` legato al nuovo
`RoutePlan`.

## Stato bounded

Il tracker possiede:

```text
RoutePlan immutabile
lista precomputata dei cursori manovra
ultimo RouteProgressSnapshot accepted
```

Non conserva:

- tutti i campioni;
- tutte le decisioni;
- log crescenti;
- geometrie duplicate;
- stato del renderer;
- file o rete.

## La leg attiva

Le leg sono contigue e condividono il punto di confine:

```text
leg 0: geometry 0 .. 2
leg 1: geometry 2 .. 3
```

Policy v0:

```text
prima del punto 2 -> leg 0
al punto 2        -> leg 1
oltre il punto 2  -> leg 1
punto finale      -> ultima leg
```

Il confine appartiene quindi alla leg che inizia in quel punto. Questa scelta è
utile per mostrare le istruzioni della nuova tappa appena il confine viene
raggiunto.

La ricerca usa binary search sugli `geometryEndIndex` delle leg:

```text
O(log numero-leg)
```

Non scansiona tutte le leg a ogni campione.

## La prossima manovra

Ogni `RouteManeuverCursor` conserva:

```text
legIndex
maneuverIndex nella leg
RouteManeuver
```

Policy:

- se `fraction == 0`, una manovra sul punto corrente è ancora eleggibile;
- se `fraction > 0`, le manovre sul punto corrente sono già superate;
- a un confine condiviso, le manovre della leg precedente vengono saltate;
- la prima manovra della leg attiva o successiva viene scelta;
- in assenza di manovre il valore è `null`.

Anche questa ricerca usa binary search per geometry index e un piccolo skip dei
soli duplicati di confine:

```text
O(log numero-manovre)
```

## Arrivo

La v0 considera arrivata una posizione quando:

```text
completedGeometryIndex == route.geometry.lastIndex
fractionToNext == 0
```

`arrived` non dimostra che il veicolo sia fisicamente parcheggiato o che l'utente
abbia concluso il viaggio. È lo stato di avanzamento sulla geometria della route.

La prossima manovra può ancora essere `Arrive`, utile per HUD e voce.

## `RouteProgressSnapshot`

Contiene:

```text
MatchedRoutePosition accepted
activeLegIndex
upcomingManeuver cursor o null
arrived
```

Non contiene ancora:

- distanza percorsa;
- distanza rimanente;
- distanza alla manovra;
- ETA;
- velocità filtrata;
- stato off-route.

## Perché non calcolare distanza dall'indice

I punti della geometria non sono equidistanti:

```text
segmento A = 2 metri
segmento B = 800 metri
```

Dire “50 punti su 100” non significa “50% della distanza”. Inoltre la distanza
stradale del provider può includere curvature o costing non ricostruibili dal
solo numero di punti.

Finché il contratto non contiene lunghezze cumulative o un provider affidabile,
Travel DNA non fabbrica distanze o ETA.

## Proiezione verso `MapSceneDelta`

```kotlin
RouteProgressMapProjector.project(
    sceneId,
    routeOverlay,
    progressSnapshot,
)
```

Produce:

```text
MapSceneDelta.UpdateRouteProgress
  sceneId
  routeOverlayId
  completedGeometryIndex
  fractionToNext
```

Il projector verifica:

- route ID dello snapshot uguale alla route dell'overlay;
- indice contenuto nella geometria installata;
- frazione zero sul punto finale.

Non copia la geometria e non crea GeoJSON.

## Percorso caldo target

```text
LocationSample
-> filter
-> map matcher
-> MatchedRoutePosition
-> RouteProgressTracker.accept
-> RouteProgressSnapshot
-> RouteProgressMapProjector
-> MapSceneDelta.UpdateRouteProgress
-> renderer adapter
```

Il tracker non contiene:

- plugin lookup;
- parser;
- database;
- rete;
- logging verboso;
- serializzazione;
- UI.

## Fixture didattica

Il Lab usa una route sintetica code-defined con:

```text
4 punti geometrici
2 leg
confine condiviso all'indice 2
manovra finale della leg 0 all'indice 2
manovra iniziale della leg 1 all'indice 2
6 posizioni matched
1 regressione intenzionale
```

Metadati:

- [`reference-route-progress-v0.meta.yaml`](../../fixtures/navigation/reference-route-progress-v0.meta.yaml)
- [policy fixture navigation](../../fixtures/navigation/README.md)

La fixture resta in Kotlin perché questa slice studia la semantica del tracker,
non un nuovo formato di serializzazione. Un file parser verrà introdotto quando
esisterà un vero requisito di import/replay matched.

## Output del Lab

```bash
sh tools/tdna lab route-progress
```

Forma:

```json
{"scenario":"reference-route-progress-v0","accepted":5,"rejected":1,"rejection":"RegressedAlongRoute","boundary_leg":1,"boundary_maneuver":"Continue","completed_index":3,"fraction":0.0,"active_leg":1,"upcoming_maneuver":"Arrive","arrived":true,"delta_index":3}
```

Il Lab verifica internamente il ground truth prima di stampare.

## Mappa del codice

```text
Main.runLab
-> referenceRoute
-> MatchedRoutePosition list
-> RouteProgressTracker.accept
   -> rejection
   -> findActiveLegIndex
   -> findUpcomingManeuver
   -> RouteProgressSnapshot
-> RouteProgressMapProjector.project
-> canonical JSON report
```

Ownership:

| Componente | Stato | Frequenza |
| --- | --- | --- |
| `RoutePlan` | geometria/leg/manovre immutabili | installazione route |
| `MatchedRoutePosition` | un output matcher | per campione |
| `RouteProgressTracker` | ultimo snapshot | per sessione |
| `RouteProgressSnapshot` | read model compatto | per accepted |
| projector | nessuno | per aggiornamento mappa |
| renderer | geometria installata e progress | superficie mappa |

## Tracepoint logici

```text
MATCHED_ROUTE_POSITION_RECEIVED
MATCHED_ROUTE_POSITION_REJECTED
ROUTE_PROGRESS_ACCEPTED
ROUTE_ACTIVE_LEG_CHANGED
ROUTE_UPCOMING_MANEUVER_CHANGED
ROUTE_ARRIVAL_REACHED
MAP_PROGRESS_DELTA_PROJECTED
```

Sono nomi documentali, non logging automatico.

## Test

La slice verifica:

- indice/frazione e zero firmato;
- lateral distance bounded;
- route mismatch;
- indice fuori geometria;
- frazione invalida sul finale;
- sequence e tempo non crescenti;
- regressione di indice e frazione;
- input rifiutato senza mutazione;
- posizione stazionaria accepted;
- boundary policy fra due leg;
- priorità della manovra della nuova leg;
- arrivo;
- `inspect` non mutante;
- `reset`;
- overlay route mismatch e geometria troncata;
- JVM/Linux x64;
- architecture boundaries.

## Benchmark diagnostico

```bash
sh tools/tdna bench route-progress 10000 7
```

Scenario:

- 10.000 posizioni accepted;
- geometria con 10.000 punti;
- fino a 100 leg;
- una manovra per leg più `Arrive`;
- tre warm-up;
- sette run;
- min, mediana e max;
- nessuna soglia CI.

La costruzione di route e input avviene fuori dalla sezione misurata. Il dato
misura tracker, ricerche binarie, snapshot e state commit.

Non misura:

- GPS;
- filtro;
- map matching;
- projector/renderer;
- rete o database;
- batteria;
- dispositivo mobile;
- affidabilità su strada.

Il risultato osservato viene registrato nel report giornaliero indicizzato.

## Alternative considerate

### Usare direttamente `RouteOverlayProgress`

Scartato: farebbe dipendere il runtime navigation dal contratto di rendering.
`RouteCoordinate` appartiene al dominio navigation e viene proiettato dopo.

### Rifiutare coordinate uguali

Scartato: un veicolo fermo produce campioni nuovi senza avanzamento geometrico.

### Correggere automaticamente le regressioni

Scartato: nasconde il comportamento del matcher. Una futura policy di isteresi
deve essere esplicita e testabile.

### Scansione lineare di leg e manovre

Scartata per il percorso caldo: il costo crescerebbe con la route. La v0 usa
binary search.

### Calcolare ETA dalla frazione geometrica

Scartato: non esiste una relazione affidabile fra numero di punti e distanza o
tempo.

### Integrare subito map matching

Scartato: confonderebbe ricerca candidata, scoring e progress tracking in una
sola slice difficile da insegnare e sostituire.

## Errori comuni

- chiamare `MatchedRoutePosition` un GPS fix;
- credere che confidence sia una probabilità universale;
- trattare lateral distance come off-route già deciso;
- rifiutare un veicolo fermo;
- accettare una regressione senza policy;
- attribuire il confine alla leg precedente senza documentarlo;
- scegliere una manovra della leg precedente al confine;
- ricostruire la geometria a ogni update;
- calcolare distanza da indice/frazione;
- usare lo stesso route ID per geometrie diverse;
- trasformare il benchmark CI in SLA.

## Esercizi

1. Aggiungere una posizione stationary con confidence `Low` e spiegare perché il
   tracker la accetta.
2. Creare una policy separata che sospende la guidance con confidence `Low`.
3. Aggiungere tre manovre sullo stesso geometry index e definire il tie-break.
4. Progettare cumulative segment distances senza dipendere dal renderer.
5. Aggiungere un test con 100 leg per verificare i confini.
6. Disegnare il contratto di un vero map matcher.
7. Progettare una isteresi per piccole regressioni senza mutare il tracker base.
8. Collegare uno snapshot accepted a un fake renderer.
9. Misurare benchmark con 1, 10 e 100 leg.
10. Progettare la route replacement senza riusare route ID.

## Non-obiettivi

- map matching reale;
- filtro GPS;
- distanza o ETA;
- off-route;
- rerouting;
- voce;
- adapter Android/iOS;
- MapLibre;
- dati stradali reali;
- prestazioni di produzione.

## Documenti successivi

- [Scenario Lab route progress](lab/scenarios/route-progress-tracker.md)
- [Mappa codice e stati](40-mappa-codice-e-stati.md)
- [Tracepoint Model](41-tracepoint-model-v0.md)
- [Scenario missed exit](lab/scenarios/navigation-missed-exit-reroute.md)
- [Registro milestone](50-registro-milestone.md)
