# Aufgabe: shop-service

Nimmt Bestellungen an. Pro Bestellung wird erst im `inventory-service` reserviert, dann im `payment-service` bezahlt. Keine Datenbank, Bestellungen werden nicht gespeichert. In dieser Aufgabe gibt es bewusst keine Resilienz (kein Timeout, kein Retry, kein Circuit Breaker, kein Fallback): Das ist der "ohne Schutz"-Zustand für Szenario 1, Resilience4j kommt in einer eigenen Aufgabe dazu.

## Features

1. Port 8080 und `spring.mvc.problemdetails.enabled: true` in `application.yaml`. In der `pom.xml` fehlt `spring-boot-starter-validation`, die Dependency muss ergänzt werden.
2. Adressen der Dienste in der Konfiguration, eingelesen über einen `@ConfigurationProperties`-Record:
   ```
   shop:
     inventory-url: http://localhost:8081
     payment-url: http://localhost:8082
   ```
   Später stehen in Docker Compose die Dienstnamen statt `localhost` (Überschreiben per Umgebungsvariable).
3. `POST /orders`
   - Request (JSON): `productId` (nicht leer), `quantity` (Ganzzahl, mindestens 1), `totalAmount` (Gesamtbetrag der Bestellung, größer 0). Preise gibt es nicht, der Gesamtbetrag kommt vom Aufrufer und geht unverändert an die Zahlung.
   - Ablauf:
     1. `orderId` erzeugen (neue UUID).
     2. Beim Lager reservieren: `POST {inventory-url}/inventory/{productId}/reservations` mit `{"quantity": ...}`.
     3. Bezahlen: `POST {payment-url}/payments` mit `{"orderId": "<orderId>", "totalAmount": ...}`.
   - Erfolg: `201` mit JSON `orderId`, `productId`, `quantity`, `reservationId`, `paymentId`, `status` (`CONFIRMED`, als Enum wie `PaymentStatus`).
   - Eingabe ungültig: `400`.
   - Produkt unbekannt (Lager antwortet `404`): `404`.
   - Nicht genug Bestand (Lager antwortet `409`): `409`. Die Zahlung wird dann nicht aufgerufen.
   - Lager oder Zahlung nicht erreichbar oder antworten mit einem anderen Fehler (z. B. `5xx`): `502 Bad Gateway`, `detail` nennt den betroffenen Dienst.
4. Logging: Pro Bestellung eine Zeile auf INFO (`orderId`, `productId`, `quantity`, Ergebnis), bei Fehlern eine Zeile auf WARN mit dem betroffenen Dienst.

## Vorgaben zur Umsetzung

- Aufrufe mit `RestClient` (Bestandteil von Spring Boot). Für jeden Dienst eine eigene Klasse: `InventoryClient` und `PaymentClient`. Szenario 1 schaltet später nur diese beiden Klassen ab, der Rest bleibt unverändert, deshalb dürfen `OrderService` und `OrderController` nichts von HTTP wissen.
- `OrderController` (HTTP, Validierung), `OrderService` (Ablauf, `orderId`), zwei Clients (HTTP zu den anderen Diensten). Packages nach Schicht unter `de.cooperr.chaos.shop`: `controller`, `service`, `client`, `dto`, `exception`, `config`.
- Records für Request und Response, Bean Validation wie im `payment-service`.
- Fehler auf Statuscodes abbilden wie im `inventory-service`: eigene Exceptions, die von `ErrorResponseException` erben. Die Clients übersetzen die Antworten der anderen Dienste in diese Exceptions (`404` zu `ProductNotFoundException`, `409` zu `InsufficientStockException`, alles andere zu einer eigenen Exception für `502`). Wie der `RestClient` Fehlerstatus und Verbindungsfehler meldet, bitte in der Spring-Doku bzw. im Quelltext nachsehen (Stichwort `onStatus`), nicht raten.
- Keine Timeouts konfigurieren und nichts wiederholen. Das ist Absicht, kein Versäumnis.

## Clients (zweiter Schritt)

Ersetzt die Rümpfe von `InventoryClient` und `PaymentClient`. Die Signaturen bleiben, `OrderService` und seine Tests ändern sich nicht.

1. `ShopProperties` als `@ConfigurationProperties("shop")`-Record mit `inventoryUrl` und `paymentUrl`, wie `InventoryProperties` im `inventory-service`. Registriert wird er in `ShopServiceApplication` mit `@EnableConfigurationProperties(ShopProperties.class)`.
2. Gemeinsame Basisklasse `ServiceClient` (paketprivat, abstrakt): Sie hält den `RestClient` und die Methode `call(Supplier<T>)`, die `RestClientException` und leere Antworten in eine `DownstreamServiceException(service, cause)` übersetzt. Jeder Client erbt davon und gibt Dienstnamen und URL aus `ShopProperties` weiter. Der `RestClient` wird mit `RestClient.builder().baseUrl(...).build()` gebaut, nicht über den eingespritzten `RestClient.Builder`: In Boot 4 liegt die Auto-Konfiguration dafür vermutlich in einem eigenen Modul, das in der `pom.xml` des Shops nicht eingebunden ist (nicht geprüft, in `~/.m2` ist es nicht vorhanden). Jeder Client hat zwei Konstruktoren: den öffentlichen mit `ShopProperties` (`@Autowired`, weil Spring bei mehreren Konstruktoren sonst keinen wählt) und einen paketprivaten mit `RestClient` für die Tests.
3. `InventoryClient.reserve(productId, quantity)`:
   - `POST /inventory/{productId}/reservations` mit Body `{"quantity": ...}` (Record `ReservationRequest`).
   - Antwort in einen Record mit allen Feldern lesen (`ReservationResponse(UUID reservationId, String productId, int quantity)`), Rückgabe ist die `reservationId`. Alle Felder anlegen, damit es keine Frage zu unbekannten Properties gibt.
   - `404` wird zu `ProductNotFoundException`, `409` zu `InsufficientStockException`, jeder andere Fehler (Status 4xx oder 5xx, Verbindung nicht möglich) zu `DownstreamServiceException("inventory-service", cause)`.
4. `PaymentClient.pay(orderId, totalAmount)`:
   - `POST /payments` mit Body `{"orderId": "<orderId>", "totalAmount": ...}` (Record `PaymentRequest`, `orderId` als String).
   - Antwort in einen Record mit allen Feldern (`PaymentResponse(UUID paymentId, String orderId, String status)`), Rückgabe ist die `paymentId`.
   - Jeder Fehler (Status oder Verbindung) wird zu `DownstreamServiceException("payment-service", cause)`.
5. Wie der `RestClient` Fehler meldet (in den Quellen von `spring-web` 7.0.9 gelesen): Eigene `onStatus`-Handler laufen vor dem Standard-Handler, der erste passende gewinnt. Der Standard-Handler wirft für jeden Fehlerstatus eine `RestClientResponseException` (Unterklassen `HttpClientErrorException` und `HttpServerErrorException`). Eine `IOException` (Verbindung) wird zu `ResourceAccessException`. Beide erben von `RestClientException`. Exceptions aus einem `onStatus`-Handler laufen unverändert durch, deshalb werden `ProductNotFoundException` und `InsufficientStockException` von `call` nicht übersetzt.
6. Der `ErrorHandler` in `onStatus` hat die Form `(request, response) -> { ... }` und darf eine Exception werfen.

## Tests (Akzeptanzkriterien)

Konventionen wie in `CLAUDE.md` (Klassen auf `Test`, paketprivat, `methode_zustand_ergebnis`, Variablen zuerst).

- `OrderServiceTest`: reiner Unit-Test ohne Spring, die beiden Clients gemockt.
  - Erfolg: Ergebnis enthält `reservationId` und `paymentId`, beide Clients wurden aufgerufen.
  - Zu wenig Bestand: Exception, `PaymentClient` wird nicht aufgerufen (`verify(..., never())`).
  - Zahlung schlägt fehl: Exception.
- `OrderControllerTest`: `@WebMvcTest`, `OrderService` gemockt mit `@MockitoBean`.
  - Erfolg: `201` mit `status`.
  - `quantity` ist 0, `totalAmount` ist 0 und `productId` fehlt: je `400`, der Service wird nicht aufgerufen.
  - Produkt unbekannt: `404`.
  - Zu wenig Bestand: `409`.
  - Downstream-Fehler: `502`.
- `InventoryClientTest` und `PaymentClientTest`: Unit-Tests mit `MockRestServiceServer` (`bindTo(RestClient.Builder)`), der Client wird über den paketprivaten Konstruktor mit dem gebundenen `RestClient` erzeugt.
  - Erfolg: richtige URL, Methode und Body werden gesendet, die ID aus der Antwort wird zurückgegeben.
  - `InventoryClient`: `404` wird zu `ProductNotFoundException`, `409` zu `InsufficientStockException`.
  - Beide: `5xx`, anderer `4xx`, Verbindungsfehler und leere Antwort werden zu `DownstreamServiceException` mit dem Dienstnamen.
- `contextLoads` läuft weiter, `verify` beim Root ist grün.

## Manuell prüfen

Alle drei Dienste starten (shop 8080, inventory 8081, payment 8082).

```
curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"productId":"watch-1","quantity":1,"totalAmount":99.90}'
curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"productId":"strap-1","quantity":60,"totalAmount":10}'
curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"productId":"nope","quantity":1,"totalAmount":10}'
```

Erwartet: `201`, `409`, `404`. Danach den `payment-service` beenden und die erste Bestellung wiederholen: `502`.

## Nicht Teil dieser Aufgabe

- Resilienz (Timeout, Retry, Circuit Breaker, Fallback), kommt mit Szenario 1
- Reservierung wieder freigeben, wenn die Zahlung fehlschlägt (das Lager kann nicht stornieren)
- Bestellungen speichern oder abfragen (`GET /orders`)
- Docker, Chaos-Endpunkt, Metriken

## Ausblick: Szenario 1 mit zwei Shops

Damit man Vorher und Nachher gleichzeitig sieht, gibt es später keinen Umschalter, sondern zwei Instanzen desselben Codes: `shop-plain` ohne und `shop-resilient` mit Resilienz, per Konfiguration gewählt. Der Lastgenerator schickt dieselbe Last an beide, das Dashboard zeigt sie nebeneinander. Noch nicht entschieden, das kommt mit der Aufgabe zu Szenario 1.
