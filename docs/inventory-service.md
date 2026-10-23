# Aufgabe: inventory-service

Verwaltet den Lagerbestand. Der Shop fragt hier die Verfügbarkeit ab und reserviert Ware. Keine Datenbank: Der Bestand liegt im Speicher und wird beim Start aus der Konfiguration geladen. Später Ziel für Szenario 2 (Ausfall) und Szenario 4 (zwei Instanzen).

## Features

1. Port 8081 in `application.yaml`.
2. Startbestand aus der Konfiguration, eingelesen über einen `@ConfigurationProperties`-Record:
   ```
   inventory:
     initial-stock:
       watch-1: 1000000
       watch-2: 1000000
       strap-1: 50
   ```
   `strap-1` mit wenig Bestand ist Absicht, damit sich "ausverkauft" zeigen lässt.
3. `GET /inventory/{productId}`
   - `200` mit JSON `productId`, `available` (Ganzzahl)
   - Unbekanntes Produkt: `404`
4. `POST /inventory/{productId}/reservations`
   - Request (JSON): `quantity` (Ganzzahl)
   - Erfolg: `201` mit JSON `reservationId` (neue UUID), `productId`, `quantity`, der Bestand sinkt um `quantity`
   - `quantity` fehlt oder kleiner als 1: `400`
   - Unbekanntes Produkt: `404`
   - Nicht genug Bestand: `409 Conflict`, der Bestand bleibt unverändert
5. Logging: Pro Reservierung eine Zeile auf INFO mit `productId`, `quantity` und dem neuen Bestand.

## Vorgaben zur Umsetzung

- Controller und Service getrennt: `InventoryController` (HTTP), `InventoryService` (Bestand, Reservierung).
- Thread-Sicherheit: Prüfen und Abziehen müssen atomar passieren, sonst wird mehr verkauft als da ist. Tipp: `ConcurrentHashMap` mit `compute(...)`.
- Fehler auf Statuscodes abbilden: Der Service wirft eigene Exceptions (z. B. `ProductNotFoundException`, `InsufficientStockException`), die zu `404` bzw. `409` werden. Entweder erben die Exceptions von `ErrorResponseException`, oder `@ExceptionHandler` bzw. `@RestControllerAdvice` übernimmt die Abbildung. Spring antwortet dann mit `ProblemDetail` (RFC 9457).
- Records für Request und Response, Bean Validation wie im `payment-service`.
- Package by feature: alles in `de.cooperr.chaos.inventory`.

## Tests (Akzeptanzkriterien)

- `InventoryServiceTest`: reiner Unit-Test ohne Spring, Service mit `new` und Testbestand erzeugen.
  - Bestand abfragen
  - Reservieren verringert den Bestand
  - Zu viel reservieren wirft eine Exception, der Bestand bleibt gleich
  - Unbekanntes Produkt wirft eine Exception
  - Nebenläufigkeit: Bei Bestand 50 reservieren 100 Threads gleichzeitig je 1 Stück. Danach haben genau 50 Reservierungen geklappt und der Bestand ist 0. Tipp: `ExecutorService` und `CountDownLatch`.
- `InventoryControllerTest`: `@WebMvcTest`, Service gemockt mit `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`; `@MockBean` gibt es in Boot 4 nicht mehr).
  - `GET` auf bekanntes Produkt: `200` mit `available`
  - `GET` auf unbekanntes Produkt: `404`
  - Reservierung klappt: `201`
  - `quantity` ist 0: `400`
  - Zu wenig Bestand: `409`
- `contextLoads` läuft weiter, `verify` beim Root ist grün.

## Manuell prüfen

```
curl -i localhost:8081/inventory/strap-1
curl -i -X POST localhost:8081/inventory/strap-1/reservations -H 'Content-Type: application/json' -d '{"quantity":60}'
```

Erwartet: erst `200` mit `available: 50`, dann `409`.

## Nicht Teil dieser Aufgabe

- Reservierungen stornieren oder freigeben
- Persistenz
- Chaos-Endpunkt

## Bekannte Einschränkung

In Szenario 4 laufen zwei Instanzen, jede mit eigenem Bestand im Speicher. Für die Demo in Ordnung (es geht um Erreichbarkeit, nicht um korrekten Gesamtbestand). Noch nicht entschieden.
