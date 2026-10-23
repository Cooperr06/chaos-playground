# Chaos Playground

Ein kleiner Online-Shop aus mehreren Spring-Boot-Diensten. Ein Chaos-Controller sabotiert ihn gezielt (Dienste stoppen, Latenz, Fehler), und die Dienste wehren sich mit Resilienz-Mustern (Circuit Breaker, Retry, Timeout, Fallback). Ein Dashboard zeigt live, wie das System reagiert.

## Architektur

```
            +------------------+
  Last  --> |  shop-service    |  nimmt Bestellungen an
(Generator) +------------------+
              |            |
              v            v
   +-------------------+  +-------------------+
   | inventory-service |  | payment-service   |
   | (Lagerbestand)    |  | (Zahlung)         |
   +-------------------+  +-------------------+

   +-------------------+       +-------------+
   | chaos-controller  | ----> | Docker API  |  stoppt, pausiert und
   | (Knoepfe im UI)   |       +-------------+  verlangsamt Container
   +-------------------+

   Dashboard: Erfolgsrate, Latenz, Zustand der Circuit Breaker (live)
```

## Module

| Modul | Aufgabe |
|---|---|
| `shop-service` | Nimmt Bestellungen an, ruft Lager und Zahlung auf, enthält die Resilienz-Muster |
| `inventory-service` | Lagerbestand, läuft später in zwei Instanzen |
| `payment-service` | Zahlung |
| `chaos-controller` | Steuert das Chaos (Docker-API und Chaos-Endpunkte der Dienste), später mit Dashboard |
| `load-generator` | Schickt dauerhaft Bestellungen an den Shop, z. B. 10 pro Sekunde |

Jeder Dienst ist eine eigene Spring-Boot-App in einem eigenen Container, gestartet mit Docker Compose.

## Szenarien

1. **Zahlung wird langsam** (5 s Latenz): Ohne Schutz blockieren die Threads des Shops, auch unbeteiligte Anfragen hängen (Kaskadenfehler). Mit Timeout und Circuit Breaker öffnet der Breaker nach zu vielen Fehlern, der Shop antwortet sofort mit einem Fallback ("Zahlung wird nachgeholt"). Nach Entfernen der Latenz: HALF_OPEN, dann CLOSED. Zum Vergleich laufen zwei Instanzen des Shops nebeneinander, eine ohne und eine mit Schutz, mit derselben Last (Plan, noch nicht entschieden).
2. **Lager fällt komplett aus** (Container gestoppt): Retry mit Backoff fängt kurze Aussetzer ab, danach Fallback (zuletzt bekannter Bestand oder "Verfügbarkeit wird geprüft"). Nach dem Neustart läuft alles automatisch weiter.
3. **Zufällige Fehler** (30 % HTTP 500 bei der Zahlung): Retry fängt einzelne Fehler ab, das Dashboard zeigt, wie viele Anfragen erst im zweiten Versuch klappten.
4. **Eine von zwei Lager-Instanzen fällt aus**: Der Shop verteilt die Anfragen auf die verbleibende Instanz.

Chaos entsteht über zwei Wege: Ausfall per Docker-API (Container stoppen oder pausieren) und Latenz/Fehler über einen Chaos-Endpunkt in jedem Dienst, z. B. `POST /chaos {"latencyMs": 5000, "errorRate": 0.3}`. Alternative wäre das fertige Tool Toxiproxy; selbst bauen ist für den Lerneffekt spannender.

## Technik

- Spring Boot (Version von start.spring.io bzw. IntelliJ beim Anlegen), Maven
- Resilience4j für Circuit Breaker, Retry, Timeout
- Actuator und Micrometer für Metriken und Breaker-Zustände
- Server-Sent Events für das Live-Dashboard
- Docker Compose
- Nginx Proxy Manager für `chaosplayground.cooperr.de` auf dem Raspberry Pi
- GitHub Actions für die CI-Pipeline

## Starten

```
docker compose up --build
docker compose down
```

Shop, Lager und Zahlung laufen dann auf `127.0.0.1:8080`, `8081` und `8082`. Mit `-d` startet alles im Hintergrund.

## Plan

- **Fertig:** `payment-service`, `inventory-service` und `shop-service` (ohne Resilienz) mit Tests, Dockerfiles, CI-Pipeline mit Images auf `ghcr.io`.
- **Als Nächstes:** Docker Compose abnehmen, Chaos-Endpunkt, Lastgenerator, Szenario 1 mit Circuit Breaker.
- **Danach:** Chaos-Controller mit Weboberfläche, Live-Dashboard, Szenarien 2 bis 4.
- **Zugang:** Das Dashboard bekommt zwei Logins, `viewer` (nur ansehen) und `admin` (zusätzlich Chaos-Knöpfe), damit Gäste die Demo selbst ausprobieren können.

## Lizenz

Apache License 2.0, siehe `LICENSE` und `NOTICE`.
