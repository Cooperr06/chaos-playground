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
   | (Lager)           |  | (Zahlung)         |
   +-------------------+  +-------------------+

   +-------------------+       +-------------+
   | chaos-controller  | ----> | Docker API  |  stoppt, pausiert und
   | (Knoepfe im UI)   |       +-------------+  verlangsamt Container
   +-------------------+

   Dashboard: Erfolgsrate, Latenz, Zustand der Circuit Breaker (Prometheus, Grafana)
```

## Module

| Modul | Aufgabe |
|---|---|
| `shop-service` | Nimmt Bestellungen an, ruft Lager und Zahlung auf, enthält die Resilienz-Muster |
| `inventory-service` | Reserviert Produkte (ohne Bestand, damit es unter Dauerlast nie leer wird), läuft später in zwei Instanzen |
| `payment-service` | Zahlung |
| `chaos-controller` | Steuert das Chaos (Docker-API und Chaos-Endpunkte der Dienste) und die Last, bettet die Graphen aus Grafana ein |
| `load-generator` | Schickt dauerhaft Bestellungen an den Shop, z. B. 10 pro Sekunde |

Jeder Dienst ist eine eigene Spring-Boot-App in einem eigenen Container, gestartet mit Docker Compose.

## Szenarien

1. **Zahlung wird langsam** (5 s Latenz): Ohne Schutz blockieren die Threads des Shops, auch unbeteiligte Anfragen hängen (Kaskadenfehler). Mit Timeout und Circuit Breaker öffnet der Breaker nach zu vielen Fehlern, der Shop antwortet sofort mit einem Fallback ("Zahlung wird nachgeholt"). Nach Entfernen der Latenz: HALF_OPEN, dann CLOSED. Zum Vergleich laufen zwei Instanzen des Shops nebeneinander, eine ohne und eine mit Schutz, mit derselben Last.
2. **Lager fällt komplett aus** (Container gestoppt): Retry mit Backoff fängt kurze Aussetzer ab, danach Fallback ("Verfügbarkeit wird geprüft"). Nach dem Neustart läuft alles automatisch weiter.
3. **Zufällige Fehler** (30 % HTTP 500 bei der Zahlung): Retry fängt einzelne Fehler ab, das Dashboard zeigt, wie viele Anfragen erst im zweiten Versuch klappten.
4. **Eine von zwei Lager-Instanzen fällt aus**: Der Shop verteilt die Anfragen auf die verbleibende Instanz.

Chaos entsteht über zwei Wege: Ausfall per Docker-API (Container stoppen oder pausieren) und Latenz/Fehler über einen Chaos-Endpunkt in jedem Dienst, z. B. `POST /chaos {"latencyMs": 5000, "errorRate": 0.3}`. Alternative wäre das fertige Tool Toxiproxy; selbst bauen ist für den Lerneffekt spannender.

## Technik

- Spring Boot (Version von start.spring.io bzw. IntelliJ beim Anlegen), Maven
- Resilience4j für Circuit Breaker, Retry, Timeout
- Actuator und Micrometer für Metriken und Breaker-Zustände
- Prometheus sammelt die Metriken, Grafana zeigt sie als Dashboard (`docs/dashboard.md`)
- Docker Compose
- Traefik und Authelia für `chaosplayground.cooperr.de` auf dem Raspberry Pi (`docs/login.md`)
- GitHub Actions für die CI-Pipeline

## Starten

Voraussetzung: Docker mit Docker Compose. Für Build und Tests ohne Docker zusätzlich ein JDK 25.

### Bauen und testen

```
./mvnw verify
```

### Ohne Login

Für die Dienste, `curl` und Grafana. Die Oberfläche des Controllers ist so gesperrt (`401`), sie braucht den Login (nächster Abschnitt).

```
docker compose up --build
docker compose down
```

Shop ohne Resilienz, Lager, Zahlung, Shop mit Resilienz, Chaos-Controller und Lastgenerator laufen dann auf `127.0.0.1:8080`, `8081`, `8082`, `8083`, `8090` und `8091`. Actuator der beiden Shops liegt auf `9080` und `9083`. Grafana liegt unter `http://localhost:3000/grafana`, Prometheus unter `http://localhost:9090`. Mit `-d` startet alles im Hintergrund.

Der Controller lässt sich mit den Headern ansprechen, die sonst Traefik setzt, z. B.:

```
curl localhost:8090/services -H 'Remote-User: guest' -H 'Remote-Groups: chaos-viewer'
```

### Mit Login (wie auf dem Pi)

Traefik und Authelia davor, Anmeldung im Browser unter `https://chaosplayground.local.cooperr.de`. Hintergrund und Prüfschritte: `docs/local-auth.md`.

Die Hostnamen brauchen keine Einrichtung: Im DNS zeigt `*.local.cooperr.de` auf `127.0.0.1`. Falls der Router solche Antworten blockiert (DNS-Rebind-Schutz, z. B. FritzBox), dort eine Ausnahme für `local.cooperr.de` eintragen. Prüfen mit `dig chaosplayground.local.cooperr.de`.

Traefik nimmt lokal ein selbst signiertes Zertifikat (Schritt 3 unten). Der Browser warnt deshalb beim ersten Aufruf von `chaosplayground.local.cooperr.de` und `auth.local.cooperr.de`, die Warnung jeweils wegklicken. Das Zertifikat bleibt über Neustarts gleich, die Ausnahme im Browser also auch. Adminrechte sind nicht nötig.

Einmal pro Rechner einrichten:

1. Secrets für Authelia:
   ```
   mkdir -p local-auth/secrets
   for name in session_secret storage_encryption_key jwt_secret; do openssl rand -hex 32 > local-auth/secrets/$name; done
   ```
2. Testbenutzer `admin` (Passwort `admin`) und `viewer` (Passwort `viewer`): Für beide Passwörter einen Hash erzeugen
   ```
   docker run --rm authelia/authelia:4.39.28 authelia crypto hash generate argon2 --password 'admin'
   docker run --rm authelia/authelia:4.39.28 authelia crypto hash generate argon2 --password 'viewer'
   ```
   und damit `local-auth/authelia/users_database.yml` anlegen (Hash ist der Teil nach `Digest: `):
   ```
   users:
     admin:
       displayname: 'Test Admin'
       password: '<hash von admin>'
       email: 'admin@local.cooperr.de'
       groups:
         - 'chaos-admin'
     viewer:
       displayname: 'Test Viewer'
       password: '<hash von viewer>'
       email: 'viewer@local.cooperr.de'
       groups:
         - 'chaos-viewer'
   ```
3. Zertifikat für Traefik:
   ```
   mkdir -p local-auth/traefik/certs
   openssl req -x509 -newkey rsa:2048 -nodes -days 825 -subj '/CN=*.local.cooperr.de' -addext 'subjectAltName=DNS:*.local.cooperr.de' -keyout local-auth/traefik/certs/local.key -out local-auth/traefik/certs/local.crt
   ```

Secrets, Benutzer und Zertifikat stehen in der `.gitignore`. Danach starten:

```
docker compose -f docker-compose.yml -f docker-compose.auth.yml up --build
docker compose -f docker-compose.yml -f docker-compose.auth.yml down
```

`admin` darf Chaos setzen, Container steuern und die Last ändern, `viewer` nur zusehen. Abmelden über den Namen unten in der Sidebar. Anmeldungen liegen in Redis und überstehen Neustarts, `down -v` löscht sie.

## Plan

- **Fertig:** `payment-service`, `inventory-service` und `shop-service` mit Tests, Dockerfiles, Docker Compose, CI-Pipeline mit Images auf `ghcr.io` und Smoke-Test, Chaos-Endpunkt in Zahlung und Lager, Lastgenerator, Chaos-Controller mit Oberfläche und Docker-Zugriff über einen Socket-Proxy, Dashboard mit Prometheus und Grafana, Login über Authelia mit lokaler Testumgebung (Traefik, Authelia, Redis). Szenario 1 (Timeout, Circuit Breaker und Fallback, zwei Shops nebeneinander, `docs/scenario-1.md`).
- **Als Nächstes:** Betrieb auf dem Pi hinter Traefik und Authelia des Homelabs, Deployment über Komodo.
- **Danach:** Szenarien 2 bis 4.
- **Zugang:** Anmeldung über Authelia mit zwei Rollen, `viewer` (nur ansehen) und `admin` (zusätzlich Chaos-Knöpfe), damit Gäste die Demo selbst ausprobieren können.

## Lizenz

Apache License 2.0, siehe `LICENSE` und `NOTICE`.
