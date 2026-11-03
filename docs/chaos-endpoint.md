# Aufgabe: Chaos-Endpunkt

Jeder sabotierbare Dienst bekommt einen Endpunkt `/chaos`, über den sich künstliche Latenz und zufällige Fehler ein- und ausschalten lassen. Das ist die Grundlage für Szenario 1 (Zahlung wird langsam) und Szenario 3 (30 % HTTP 500). Später ruft der `chaos-controller` diese Endpunkte auf, bis dahin per `curl`. Den Ausfall eines ganzen Dienstes (Szenario 2 und 4) erledigt die Docker-API, dafür ist dieser Endpunkt nicht gedacht.

## Umfang

- `payment-service` und `inventory-service`. Nur diese beiden werden in den Szenarien sabotiert. Der Shop bekommt keinen Chaos-Endpunkt, er ist der Aufrufer, der sich wehren soll.
- Die Klassen entstehen in beiden Modulen getrennt (eine Kopie pro Dienst). Es gibt weiter kein Modul `common`. Wenn der `chaos-controller` dazukommt und dieselben DTOs braucht, wird neu entschieden, ob daraus ein gemeinsames Modul wird.
- Reihenfolge: erst `payment-service` komplett mit Tests, dann in den `inventory-service` übertragen.

## Features

1. `POST /chaos` setzt das Verhalten
   - Request (JSON): `latencyMs` (Ganzzahl, Millisekunden) und `errorRate` (Kommazahl von 0.0 bis 1.0, Wahrscheinlichkeit pro Anfrage). Beide Felder sind Pflicht.
   - Der Aufruf ersetzt den gesamten Zustand. Zurückgesetzt wird mit `{"latencyMs":0,"errorRate":0}`, ein eigener Endpunkt ist nicht nötig.
   - Erfolg: `200` mit dem jetzt gültigen Zustand (gleiche Felder wie der Request).
   - `latencyMs` kleiner 0 oder größer 30000, `errorRate` kleiner 0 oder größer 1, ein fehlendes Feld, fehlender oder unlesbarer Body: `400`. Fehlende Felder lehnt Jackson 3 bei `int` und `double` von selbst ab (`FAIL_ON_NULL_FOR_PRIMITIVES` ist seit Jackson 3.0 standardmäßig an).
2. `GET /chaos` liefert den aktuellen Zustand: `200` mit `latencyMs` und `errorRate`. Ohne vorherigen `POST` sind beide 0.
3. Wirkung auf die Fachendpunkte (`/payments/**` bzw. `/inventory/**`):
   - Zuerst wartet die Anfrage `latencyMs` Millisekunden, danach entscheidet der Zufall: Mit Wahrscheinlichkeit `errorRate` antwortet der Dienst mit `500`, sonst läuft die Anfrage normal weiter. Die Reihenfolge ist Absicht: Ein langsamer Fehler ist das realistischere Bild.
   - Die Wartezeit blockiert den Thread der Anfrage (kein asynchrones Warten). Genau das erschöpft in Szenario 1 die Threads des Shops, es ist kein Versäumnis.
   - Fehlerantwort: `500` als `ProblemDetail` (RFC 9457), `detail` nennt, dass der Fehler absichtlich ausgelöst wurde.
4. `/chaos` selbst und `/actuator/**` bleiben ohne Chaos. Der Zustand muss sich immer zurücksetzen lassen, und `/actuator/health` (Smoke-Test in der CI, später Healthchecks) darf nicht von der Latenz abhängen. Am einfachsten: Der Interceptor wird nur für die Fachpfade registriert, nicht für alles mit Ausnahmen.
5. Der Zustand liegt im Speicher und ist beim Start 0. Ein Neustart des Containers setzt das Chaos zurück.
6. Logging: Eine Zeile auf INFO, wenn sich der Zustand ändert (neue Werte). Pro Anfrage nur auf DEBUG, sonst läuft das Log bei 10 Anfragen pro Sekunde voll.

## Vorgaben zur Umsetzung

- Packages wie bisher nach Schicht: `ChaosSettings` (Record, `public`) in `dto`, `ChaosException` in `exception`, `ChaosService` in `service`, `ChaosController` in `controller`, `ChaosInterceptor` und `ChaosWebConfig` in `config`.
- `ChaosSettings` mit Bean Validation wie im `payment-service` (`@Min`, `@Max`, für die Kommazahl `@DecimalMin` und `@DecimalMax`), im Controller mit `@Valid`.
- `ChaosService` hält den Zustand. Er wird von mehreren Threads gelesen und geschrieben: Den unveränderlichen Record als Ganzes austauschen (z. B. `AtomicReference<ChaosSettings>`), keine einzelnen Felder synchronisieren. Der Zufall kommt aus `ThreadLocalRandom`. Ein Wert aus `nextDouble()` liegt in `[0, 1)`, damit löst `errorRate` 0.0 nie und 1.0 immer einen Fehler aus.
- `ChaosException` erbt von `ErrorResponseException` wie die übrigen Exceptions, Status `500`. Eine Exception aus `HandlerInterceptor.preHandle` landet im selben Fehlerpfad wie eine aus dem Controller (`DispatcherServlet`, spring-webmvc 7.0.9) und wird so zum `ProblemDetail`.
- `ChaosInterceptor` ist ein `HandlerInterceptor` ohne eigene Bean-Annotation. `ChaosWebConfig` (`@Configuration`, `implements WebMvcConfigurer`) erzeugt ihn und registriert ihn mit `addPathPatterns`.
- `@WebMvcTest` lädt `WebMvcConfigurer`- und `HandlerInterceptor`-Beans mit (`WebMvcTypeExcludeFilter`, Boot 4.1.1). Deshalb brauchen alle `@WebMvcTest`-Klassen des Dienstes einen `@MockitoBean ChaosService`, auch der bestehende Controller-Test.
- Keine Authentifizierung. Die Ports sind in Compose an `127.0.0.1` gebunden, öffentlich erreichbar ist später nur der `chaos-controller`. Die Dienste dürfen nie direkt ins Netz.
- Am Shop ändert sich nichts. Er bekommt keine Timeouts, die gehören zu Szenario 1.

## Tests (Akzeptanzkriterien)

- `ChaosServiceTest`: reiner Unit-Test ohne Spring.
  - Ohne `updateSettings` sind beide Werte 0.
  - Ein zweites `updateSettings` ersetzt den ersten Zustand komplett.
  - `errorRate` 0.0 löst nie einen Fehler aus, 1.0 immer. Kein statistischer Test mit Werten dazwischen, der wäre unzuverlässig.
  - Mit `latencyMs` 200 dauert `injectChaos` mindestens 200 ms (kleine Werte, damit die Tests schnell bleiben).
- `ChaosControllerTest`: `@WebMvcTest(ChaosController.class)`, Service gemockt mit `@MockitoBean`.
  - `GET` liefert `200` mit den Werten.
  - `POST` mit gültigen Werten liefert `200`.
  - `POST` mit `latencyMs` -1, `latencyMs` 30001, `errorRate` 1.5, ohne `errorRate` und ohne Body liefert jeweils `400`.
- Bestehender Controller-Test (`PaymentControllerTest` bzw. `InventoryControllerTest`) bekommt `@MockitoBean ChaosService` und bleibt sonst unverändert grün.
- `ChaosInterceptorTest`: `@SpringBootTest` mit `@AutoConfigureMockMvc` (`org.springframework.boot.webmvc.test.autoconfigure`), echter `ChaosService`, damit auch `/actuator/health` geladen ist. Der Zustand nach jedem Test zurücksetzen (`@AfterEach`), weil der Spring-Kontext zwischen Tests geteilt wird.
  - Mit `errorRate` 1.0 liefert der Fachendpunkt (`POST /payments` bzw. `GET /inventory/watch-1`) `500`.
  - Mit `errorRate` 1.0 liefern `GET /chaos` und `GET /actuator/health` weiter `200`.
  - Ohne Chaos antwortet der Fachendpunkt normal.
- `contextLoads` läuft weiter, `mvn -o verify` im Root ist grün.

## Manuell prüfen

```
curl -i -X POST localhost:8082/chaos -H 'Content-Type: application/json' -d '{"latencyMs":3000,"errorRate":0}'
time curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"productId":"watch-1","quantity":1,"totalAmount":99.90}'
curl -i -X POST localhost:8082/chaos -H 'Content-Type: application/json' -d '{"latencyMs":0,"errorRate":1.0}'
curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"productId":"watch-1","quantity":1,"totalAmount":99.90}'
curl -i -X POST localhost:8082/chaos -H 'Content-Type: application/json' -d '{"latencyMs":0,"errorRate":0}'
```

Erwartet: Die erste Bestellung dauert gut 3 Sekunden und liefert `201`. Mit `errorRate` 1.0 liefert der Shop `502` (laut `docs/shop-service.md` bildet er einen `5xx` der Zahlung auf `502` ab). Nach dem Zurücksetzen ist wieder alles wie vorher. `GET localhost:8082/actuator/health` antwortet in jedem dieser Zustände sofort.

## Nicht Teil dieser Aufgabe

- Chaos-Endpunkt im Shop
- Aufruf aus dem `chaos-controller`, Dashboard und Login
- Zeitlich begrenztes Chaos (Ablauf nach X Sekunden), Chaos nur für einzelne Pfade
- Andere Fehlercodes als `500`
- Ein gemeinsames Modul für die geteilten Klassen
