# Aufgabe: Szenario 1, Zahlung wird langsam

Der `payment-service` antwortet mit 5 Sekunden Latenz. Zwei Shops aus demselben Image laufen nebeneinander und bekommen vom Lastgenerator dieselbe Last:

- `shop-plain` ohne Schutz: Jede Bestellung blockiert einen Thread, bis die Zahlung antwortet. Bei 10 Bestellungen pro Sekunde und 5 Sekunden Latenz hängen etwa 50 Anfragen gleichzeitig, der Thread-Pool hat 20. Nach rund 2 Sekunden sind alle Threads belegt, danach hängt auch `/actuator/health`, obwohl es mit der Zahlung nichts zu tun hat (Kaskadenfehler).
- `shop-resilient` mit Timeout und Circuit Breaker: Ein Aufruf der Zahlung bricht nach 1 Sekunde ab. Nach genug Fehlschlägen öffnet der Breaker, ab dann ruft der Shop die Zahlung gar nicht mehr auf und antwortet sofort mit dem Fallback "Zahlung wird nachgeholt". Bei 10 pro Sekunde und höchstens 1 Sekunde Wartezeit sind höchstens etwa 10 Threads belegt, der Pool reicht.

Nach dem Entfernen der Latenz geht der Breaker nach der Wartezeit in HALF_OPEN, lässt einige Probeaufrufe durch und schließt wieder (CLOSED).

## Features

1. **Thread-Pool:** `server.tomcat.threads.max: 20` in der `application.yaml` des Shops, für beide Instanzen gleich. Ohne diese Einstellung (Standard 200 Threads, Boot 4.1.1) tritt der Kaskadenfehler bei 10 Bestellungen pro Sekunde nicht auf.
2. **Umschalter:** `shop.resilience-enabled` (Boolean, Standard `false`) im bestehenden `ShopProperties`-Record. Nur bei `true` gelten Timeout, Circuit Breaker und Fallback. Bei `false` verhält sich der Shop genau wie bisher.
3. **Timeout** (nur mit Resilienz): `shop.payment-timeout` (Standard `1s`) als Read-Timeout des `RestClient` im `PaymentClient`, über `JdkClientHttpRequestFactory.setReadTimeout(Duration)` wie im Lastgenerator. Ein Timeout landet als `DownstreamServiceException` beim Aufrufer, wie jeder andere Fehler der Zahlung.
4. **Circuit Breaker** (nur mit Resilienz) um die Aufrufe der Zahlung, Name `payment-service`. Konfiguration in der `application.yaml`:
   ```
   resilience4j:
     circuitbreaker:
       instances:
         payment-service:
           sliding-window-size: 10
           minimum-number-of-calls: 5
           failure-rate-threshold: 50
           wait-duration-in-open-state: 10s
           permitted-number-of-calls-in-half-open-state: 3
           automatic-transition-from-open-to-half-open-enabled: true
   ```
   Die Bedeutung jedes Werts bitte in der Resilience4j-Doku nachlesen (CircuitBreaker, Abschnitt "Create and configure a CircuitBreaker") und im Code kurz kommentieren, warum er so gewählt ist. Mit diesen Werten öffnet der Breaker nach 5 Fehlschlägen in Folge, also nach gut 1 Sekunde Last.
5. **Fallback** (nur mit Resilienz): Scheitert die Zahlung (Timeout, Fehlerstatus, Verbindungsfehler oder Breaker offen), antwortet der Shop mit `202 Accepted` und `status` `PAYMENT_PENDING`, `paymentId` ist `null`. Die Reservierung im Lager bleibt bestehen. Ohne Resilienz bleibt es bei `502`.
   - Fehler des Lagers (`404`, `409`, `502`) ändern sich nicht. Szenario 1 betrifft nur die Zahlung.
   - Das Nachholen der Zahlung gibt es nicht wirklich, `PAYMENT_PENDING` ist nur die Antwort an die Kundschaft.
6. **Actuator:** `management.endpoints.web.exposure.include: health,circuitbreakers`. `GET /actuator/circuitbreakers` zeigt Zustand und Fehlerquote des Breakers. Ohne Resilienz steht der Breaker dort auch, bleibt aber unbenutzt (`CLOSED`, `bufferedCalls` 0).
7. **Logging:** Zustandswechsel des Breakers auf INFO (`CLOSED -> OPEN` usw.), über `circuitBreaker.getEventPublisher().onStateTransition(...)`. Pro Fallback eine Zeile auf WARN wie bisher bei Fehlern.

## Vorgaben zur Umsetzung

- **Abhängigkeit:** `io.github.resilience4j:resilience4j-spring-boot4`, Version 2.4.0. Die Version steht als Property und in `<dependencyManagement>` der Root-`pom.xml`, das Shop-Modul nennt nur `groupId` und `artifactId` (Regel: Versionen nur im Root). Geprüft in einer Kopie des Shops: Mit Boot 4.1.1 startet der Kontext, die `CircuitBreakerRegistry` liest die YAML-Konfiguration, und `/actuator/circuitbreakers` antwortet. Die Bibliothek ist gegen Boot 4.0.0 gebaut.
- **Programmatisch statt Annotationen.** `@CircuitBreaker` braucht AspectJ (nicht in der Abhängigkeit enthalten) und lässt sich nicht per Property abschalten. Stattdessen holt sich der `PaymentClient` den Breaker aus der `CircuitBreakerRegistry` und gibt ihn an `ServiceClient` weiter.
- **`ServiceClient.call` bleibt die zentrale Stelle** (siehe `CLAUDE.md`). `ServiceClient` bekommt einen optionalen `CircuitBreaker`. Ist einer gesetzt, läuft der Aufruf über `circuitBreaker.executeSupplier(...)`. Ein offener Breaker wirft `CallNotPermittedException`, `call` übersetzt sie in eine `DownstreamServiceException`, so wie die übrigen Fehler. Der `InventoryClient` bekommt keinen Breaker.
- **Kein `TimeLimiter`** von Resilience4j: Der arbeitet nur mit asynchronen Aufrufen (`CompletionStage`), der `RestClient` ist synchron. Der Read-Timeout erfüllt denselben Zweck.
- **Fallback im `OrderService`**, nicht im Client: Ob eine gescheiterte Zahlung zu `502` oder zu `PAYMENT_PENDING` führt, ist eine fachliche Entscheidung. Der `OrderService` fängt die `DownstreamServiceException` der Zahlung und entscheidet anhand von `resilience-enabled`. Er weiß weiter nichts von HTTP.
- `OrderStatus` bekommt `PAYMENT_PENDING`. Der `OrderController` gibt `ResponseEntity<OrderResponse>` zurück, `201` bei `CONFIRMED`, `202` bei `PAYMENT_PENDING`.
- Die Auto-Configuration legt jede Instanz aus der YAML schon beim Start in der Registry an, auch ohne Resilienz (geprüft in einer Kopie des Shops). Ohne Resilienz gibt der `PaymentClient` den Breaker einfach nicht an `ServiceClient` weiter, er bleibt unbenutzt.

## Docker Compose, Lastgenerator und CI

- Der Dienst `shop-service` wird zu zwei Diensten aus demselben Dockerfile:
  - `shop-plain`, Container `chaos-playground-shop-plain`, Port `127.0.0.1:8080:8080`, ohne Resilienz.
  - `shop-resilient`, Container `chaos-playground-shop-resilient`, Port `127.0.0.1:8083:8080`, `SHOP_RESILIENCEENABLED: "true"`.
  - Im Container hören beide auf 8080, nur der Port auf dem Host unterscheidet sich.
- Lastgenerator: zwei Ziele, `LOADGENERATOR_TARGETURLS_0: http://shop-plain:8080` und `LOADGENERATOR_TARGETURLS_1: http://shop-resilient:8080`, `depends_on` auf beide.
- CI, Job `images`: unverändert, es bleibt ein Image `shop-service`.
- CI, Job `smoke-test`:
  - Warten auch auf Port 8083.
  - Die bestehenden Schritte laufen weiter gegen `shop-plain` auf 8080.
  - Neuer Schritt: Chaos `{"latencyMs":0,"errorRate":1.0}` am `payment-service`, Bestellung an `shop-resilient` auf 8083 liefert `202`, danach Chaos zurücksetzen. Ohne Latenz ist das sofort und eindeutig, es prüft den Fallback-Pfad im Container.
  - Im Lastgenerator-Schritt müssen Bestellungen mit `201` für beide Ziele gezählt sein.

## Tests (Akzeptanzkriterien)

- `PaymentClientTest`, zusätzlich mit Breaker: `CircuitBreaker.of("payment-service", ...)` mit kleiner Konfiguration (z. B. Fenster 2, Mindestanzahl 2) direkt im Test erzeugen, kein Spring.
  - Nach 2 Fehlern (`withServerError()`) ist der Breaker `OPEN`.
  - Bei offenem Breaker wirft `pay` eine `DownstreamServiceException`, und der Mock-Server bekommt keinen Aufruf mehr (`server.verify()` mit genau den erwarteten Aufrufen).
  - Ohne Breaker verhält sich der Client wie bisher (bestehende Tests bleiben unverändert grün).
- `OrderServiceTest`:
  - Mit Resilienz und scheiternder Zahlung: Status `PAYMENT_PENDING`, `paymentId` `null`, `reservationId` gesetzt, keine Exception.
  - Ohne Resilienz und scheiternder Zahlung: `DownstreamServiceException` wie bisher.
  - Mit Resilienz und scheiterndem Lager: `DownstreamServiceException`, kein Fallback.
- `OrderControllerTest`: `PAYMENT_PENDING` vom Service ergibt `202`, `CONFIRMED` weiter `201`.
- `contextLoads` mit und ohne `shop.resilience-enabled` (zweite Testklasse mit `@SpringBootTest(properties = "shop.resilience-enabled=true")`).
- `mvn -o verify` im Root ist grün, der Smoke-Test in der CI auch.

## Manuell prüfen (die eigentliche Demo)

```
docker compose up --build -d
curl -s -X POST localhost:8091/load -H 'Content-Type: application/json' -d '{"ratePerSecond":10}'
curl -s -X POST localhost:8082/chaos -H 'Content-Type: application/json' -d '{"latencyMs":5000,"errorRate":0}'
curl -s localhost:8091/load
time curl -s localhost:8080/actuator/health
time curl -s localhost:8083/actuator/health
curl -s localhost:8083/actuator/circuitbreakers
curl -s -X POST localhost:8082/chaos -H 'Content-Type: application/json' -d '{"latencyMs":0,"errorRate":0}'
curl -s localhost:8083/actuator/circuitbreakers
curl -s -X POST localhost:8091/load -H 'Content-Type: application/json' -d '{"ratePerSecond":0}'
docker compose down
```

Erwartet, einige Sekunden nach dem Setzen der Latenz:

- `GET /load`: Beim Ziel `shop-plain` steigt `inFlight` stark, nach 10 Sekunden kommen `timeouts` dazu. Beim Ziel `shop-resilient` bleibt `inFlight` klein, `statusCounts` zählt `202`, die Antwortzeit liegt bei wenigen Millisekunden.
- `/actuator/health`: auf 8080 sehr langsam oder gar keine Antwort, auf 8083 sofort.
- `/actuator/circuitbreakers` auf 8083: `OPEN`. Nach dem Zurücksetzen der Latenz und 10 Sekunden `HALF_OPEN`, nach 3 erfolgreichen Probeaufrufen `CLOSED`. `statusCounts` zählt wieder `201`.

## Nicht Teil dieser Aufgabe

- Retry (Szenario 3), Fallback für das Lager (Szenario 2)
- Zahlungen wirklich nachholen
- Dashboard, Metriken über Micrometer, Steuerung durch den `chaos-controller`
- Bulkhead (eigener Thread-Pool pro Zielsystem) als Alternative zum Timeout
