# Aufgabe: load-generator

Ersetzt die Kundschaft: Der Dienst schickt im festen Takt Bestellungen an den Shop und zählt, was zurückkommt. Ohne ständigen Verkehr sind die Effekte der Szenarien nicht zu sehen. Der Kaskadenfehler entsteht erst, wenn viele Anfragen gleichzeitig hängen, ein Circuit Breaker bewertet die Fehlerquote über viele Aufrufe, und das Dashboard braucht einen gleichmäßigen Strom. Der Lastgenerator zeigt die Sicht der Kundschaft: Ein Shop mit Schutz antwortet schnell mit einem Fallback, ein Shop ohne Schutz gar nicht.

## Features

1. Port 8091 und `spring.mvc.problemdetails.enabled: true` in `application.yaml`. In der `pom.xml` fehlt `spring-boot-starter-validation`, die Dependency muss ergänzt werden.
2. Konfiguration über einen `@ConfigurationProperties`-Record:
   ```
   load-generator:
     target-urls:
       - http://localhost:8080
     rate-per-second: 0
     timeout: 10s
   ```
   - `target-urls`: Basis-Adressen der Shops. Im Moment nur einer, für Szenario 1 vielleicht zwei (siehe `docs/shop-service.md`, Ausblick). Jede Anfrage geht an jedes Ziel, alle Ziele bekommen also dieselbe Last.
   - `rate-per-second`: Startwert der Rate. 0 heißt: Der Dienst sendet nach dem Start nichts, bis die Rate über den Endpunkt gesetzt wird.
   - `timeout`: Wie lange auf eine Antwort gewartet wird, danach zählt die Anfrage als Timeout.
   - Die Ziele kommen nur aus der Konfiguration, nicht über den Endpunkt. Sonst könnte jeder, der den Endpunkt erreicht, den Dienst auf beliebige Adressen schicken.
3. Jede Anfrage ist eine Bestellung: `POST {target-url}/orders` mit `{"productId":"watch-1","quantity":1,"totalAmount":99.90}`. Der Bestand von `watch-1` (1.000.000) reicht bei 10 pro Sekunde etwa 27 Stunden, ein Neustart des `inventory-service` setzt ihn zurück.
4. `POST /load` setzt die Rate
   - Request (JSON): `ratePerSecond` (Ganzzahl, 0 bis 100). 0 stoppt das Senden. Die Obergrenze schützt den Raspberry Pi.
   - Erfolg: `200` mit dem Zustand wie bei `GET /load`.
   - Wert außerhalb des Bereichs, fehlendes Feld, fehlender oder unlesbarer Body: `400`.
   - Setzt die Zähler aller Ziele auf 0, damit jede Messung bei null anfängt.
5. `GET /load` liefert den Zustand: `200` mit
   ```
   {
     "ratePerSecond": 10,
     "targets": [
       {
         "url": "http://localhost:8080",
         "sent": 120,
         "inFlight": 50,
         "statusCounts": {"201": 60, "502": 10},
         "timeouts": 0,
         "connectionErrors": 0,
         "averageDurationMs": 35,
         "maxDurationMs": 5012
       }
     ]
   }
   ```
   - `sent`: abgeschickte Anfragen. `inFlight`: abgeschickt, aber noch ohne Ergebnis. Das ist die wichtigste Zahl für Szenario 1, sie zeigt, wie viele Anfragen gerade hängen.
   - `statusCounts`: Anzahl je HTTP-Status, auch `4xx` und `5xx`. Ein Fehlerstatus ist ein Ergebnis, keine Exception.
   - `timeouts`: keine Antwort innerhalb von `timeout`. `connectionErrors`: Shop nicht erreichbar.
   - `averageDurationMs` und `maxDurationMs` über alle Anfragen mit Ergebnis seit dem letzten `POST /load` (ohne Timeouts und Verbindungsfehler). Ohne Ergebnis sind beide 0.
6. Logging: eine Zeile auf INFO, wenn sich die Rate ändert. Pro Anfrage nur auf DEBUG (wie beim Chaos-Endpunkt).

## Vorgaben zur Umsetzung

- **Senden im festen Takt, unabhängig von den Antworten.** Der Takt darf nie auf eine Antwort warten, sonst sinkt die Rate, sobald der Shop hängt, und der Shop wird entlastet. Das würde genau den Effekt verstecken, den Szenario 1 zeigen soll. Vorschlag: ein `ScheduledExecutorService` mit einem Thread, der im Abstand `1 s / ratePerSecond` pro Ziel eine Aufgabe an einen `Executors.newVirtualThreadPerTaskExecutor()` übergibt. Jede Anfrage läuft auf einem eigenen virtuellen Thread und darf dort blockieren. Bei einer neuen Rate den laufenden Takt abbrechen (`ScheduledFuture.cancel`) und neu planen.
- **Timeout:** Der `RestClient` bekommt eine `JdkClientHttpRequestFactory` mit `setReadTimeout(Duration)` (spring-web 7.0.9). Ein Timeout kommt dann als `ResourceAccessException` an, deren Ursache eine `java.net.http.HttpTimeoutException` ist (`JdkClientHttpRequest.TimeoutHandler`). Jede andere `ResourceAccessException` zählt als Verbindungsfehler.
- **Fehlerstatus ohne Exception:** Standardmäßig wirft `retrieve()` bei `4xx` und `5xx` eine Exception. Für den Lastgenerator ist nur der Status wichtig. Wie man ihn ohne Exception bekommt (z. B. `exchange` oder `onStatus`), bitte in der Spring-Doku bzw. im Quelltext nachsehen, nicht raten.
- **Zähler sind threadsicher**, weil viele virtuelle Threads gleichzeitig zählen. `AtomicLong` oder `LongAdder`, für `statusCounts` eine `ConcurrentHashMap<Integer, LongAdder>`. Keine `synchronized`-Blöcke um die Zähler.
- Packages nach Schicht unter `de.cooperr.chaos.loadgenerator`:
  - `config`: `LoadGeneratorProperties` (`@ConfigurationProperties("load-generator")`, registriert mit `@EnableConfigurationProperties` in `LoadGeneratorApplication`).
  - `controller`: `LoadController` mit `GET` und `POST /load`.
  - `service`: `LoadService` (Takt, Rate) und `TargetCounter` (paketprivat, Zähler für ein Ziel). Eine neue Rate ersetzt die `TargetCounter` durch neue, statt jedes Feld einzeln zurückzusetzen. Anfragen, die davor gestartet wurden, zählen dann in die alten, verworfenen Zähler. Das ist für eine Demo in Ordnung.
  - `client`: `OrderClient`, schickt eine Bestellung an eine Basis-Adresse und meldet das Ergebnis (Status, Timeout oder Verbindungsfehler, Dauer). Er weiß nichts vom Takt.
  - `dto`: `LoadSettings` (Request mit Bean Validation), `LoadStatus` und `TargetStatus` (Response), `OrderResult` und `OrderOutcome` (Ergebnis einer Bestellung, vom Client an den Service).
- Beim Herunterfahren beide Executoren schließen: `LoadService` implementiert `AutoCloseable`, Spring ruft `close()` bei solchen Beans von selbst auf (`DisposableBeanAdapter`, spring-beans 7.0.9). In `close()` die Executoren mit `shutdownNow()` beenden, nicht mit `close()`: `ExecutorService.close()` wartet, bis alle laufenden Anfragen fertig sind, also bis zum Timeout.

## Docker Compose und CI

- `load-generator/Dockerfile` wie die der anderen Dienste.
- In `docker-compose.yml` ein Dienst `load-generator`, Container `chaos-playground-load`, Port `127.0.0.1:8091:8091`, `depends_on: shop-service`. Ziel per Umgebungsvariable auf `http://shop-service:8080`: `LOADGENERATOR_TARGETURLS_0` (Bindestriche fallen weg, der Listenindex steht am Ende). Ausprobiert mit dem gebauten Jar, `GET /load` zeigt dann die Adresse aus der Variable. Seit Szenario 1 sind es zwei Ziele, `http://shop-plain:8080` und `http://shop-resilient:8080` (siehe `docs/scenario-1.md`).
- `load-generator` in die Matrix des Jobs `images` in `.github/workflows/ci.yml` aufnehmen. Im Smoke-Test ein Schritt, der den Lastgenerator kurz Bestellungen schicken lässt (siehe `docs/ci.md`, Stufe 3).

## Tests (Akzeptanzkriterien)

- `LoadControllerTest`: `@WebMvcTest(LoadController.class)`, `LoadService` gemockt mit `@MockitoBean`.
  - `GET` liefert `200` mit den Werten aus dem Mock.
  - `POST` mit `ratePerSecond` 10 liefert `200`.
  - `POST` mit `ratePerSecond` -1, 101 und ohne Feld liefert jeweils `400`, der Service wird nicht aufgerufen. Ohne Feld würde sonst still Rate 0 gesetzt, wenn Jackson fehlende `int`-Felder nicht mehr ablehnt. Einen Test ohne Body gibt es nicht, das prüft nur Spring selbst.
- `OrderClientTest`: `RestClient` an einen `MockRestServiceServer` gebunden, wie die Client-Tests im `shop-service`.
  - Antwort `201` ergibt Status 201.
  - Antwort `502` ergibt Status 502, ohne Exception.
  - Der Request geht als `POST` an `/orders` mit dem Body aus Feature 3.
  - Timeout: `withException(new HttpTimeoutException(...))` ergibt `TIMEOUT`. Verbindungsfehler: `withException(new IOException(...))` ergibt `CONNECTION_ERROR`. Der `RestClient` verpackt beide in eine `ResourceAccessException`, wie bei einem echten Fehler.
- `LoadServiceTest`: reiner Unit-Test ohne Spring, `OrderClient` gemockt, Ziel eine feste URL.
  - Ohne gesetzte Rate wird der Client nie aufgerufen.
  - Mit Rate 20 wird der Client nach etwa einer Sekunde ungefähr 20-mal aufgerufen. Mit Awaitility auf "mindestens 15 Aufrufe" warten statt einen genauen Wert zu prüfen, sonst ist der Test wackelig.
  - Ein Client, der 2 Sekunden blockiert, bremst den Takt nicht: Bei Rate 20 sind nach einer Sekunde trotzdem mindestens 15 Aufrufe gestartet, und `inFlight` ist größer 0.
  - Rate 0 nach Rate 20 stoppt das Senden: Danach kommen keine neuen Aufrufe mehr dazu.
  - Eine neue Rate setzt die Zähler auf 0.
  - Nach jedem Test den Service schließen (`@AfterEach`), damit keine Threads weiterlaufen.
- `contextLoads` läuft weiter, `mvn -o verify` im Root ist grün.

## Manuell prüfen

```
docker compose up --build -d
curl -i -X POST localhost:8091/load -H 'Content-Type: application/json' -d '{"ratePerSecond":10}'
curl -s localhost:8091/load
curl -i -X POST localhost:8082/chaos -H 'Content-Type: application/json' -d '{"latencyMs":0,"errorRate":1.0}'
curl -s localhost:8091/load
curl -i -X POST localhost:8082/chaos -H 'Content-Type: application/json' -d '{"latencyMs":0,"errorRate":0}'
curl -i -X POST localhost:8091/load -H 'Content-Type: application/json' -d '{"ratePerSecond":0}'
docker compose down
```

Erwartet: Nach einigen Sekunden wächst `statusCounts.201` um etwa 10 pro Sekunde. Mit Chaos am `payment-service` wächst stattdessen `502`. Nach Rate 0 bleibt `sent` stehen.

Timeout prüfen: Latenz am `payment-service` auf 15000 setzen, `timeouts` steigt nach 10 Sekunden, `inFlight` liegt bei etwa Rate mal 10. Verbindungsfehler prüfen: `docker compose stop shop-plain`, `connectionErrors` steigt.

## Nicht Teil dieser Aufgabe

- Kleinerer Thread-Pool im Shop (`server.tomcat.threads.max`). Der gehört zu Szenario 1. Bei 10 Anfragen pro Sekunde und 5 Sekunden Latenz hängen etwa 50 Anfragen gleichzeitig, der Standard von 200 Threads (Boot 4.1.1, `spring-boot-tomcat`) wäre damit nicht ausgeschöpft.
- Metriken über Micrometer für das Dashboard
- Verschiedene Produkte oder zufällige Bestellungen
- Steuerung durch den `chaos-controller`
- Ein zweiter Shop
