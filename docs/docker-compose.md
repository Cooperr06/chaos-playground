# Aufgabe: Docker Compose

Bringt `shop-service`, `inventory-service` und `payment-service` mit einem Befehl in Containern zum Laufen, lokal auf dem Mac (arm64) und später unverändert auf dem Raspberry Pi 5 (ebenfalls arm64). Der `chaos-controller` und der `load-generator` kommen in späteren Aufgaben dazu, die Struktur soll das einfach machen.

## Features

1. Je ein `Dockerfile` pro Modul (`shop-service/Dockerfile`, `inventory-service/Dockerfile`, `payment-service/Dockerfile`), später auch für `chaos-controller` und `load-generator`. Der Build-Kontext ist trotzdem das Repo-Root (`context: .` und `dockerfile: <modul>/Dockerfile` in der Compose-Datei), weil jedes Modul die Root-`pom.xml` als Parent braucht. Mehrstufig:
   - Build-Stufe: Maven und JDK 25, baut nur das eigene Modul mit seinen Abhängigkeiten (`-pl <modul> -am`), Tests laufen hier nicht (`-DskipTests`), die laufen bei `mvn verify`. Es werden nur Root-`pom.xml`, die Modul-`pom.xml` und `src` des Moduls kopiert.
   - Laufzeit-Stufe: JRE 25, nur das fertige Spring-Boot-JAR, ein Nicht-Root-Benutzer.
   - Das Basis-Image muss für `arm64` (und gern `amd64`) veröffentlicht sein. Gewählt: `maven:3.9-eclipse-temurin-25` für den Build und `eclipse-temurin:25-jre-alpine` für die Laufzeit (nur JRE, `linux/arm64/v8`, etwa 71 MB komprimiert). Alpine bringt `adduser` mit, damit lässt sich ein eigener Benutzer anlegen.
2. `.dockerignore` im Root (`target/`, `.git/`, `.idea/`, `docs/` und Ähnliches), damit der Build-Kontext klein bleibt.
3. `docker-compose.yml` im Root mit den drei Diensten:
   - Projektname `chaos-playground`, ein eigenes Netz. Die Dienste erreichen sich über ihre Dienstnamen (`inventory-service`, `payment-service`).
   - `shop-service` bekommt die Dienst-URLs per Umgebungsvariable (`shop.inventory-url` und `shop.payment-url` zeigen auf `http://inventory-service:8081` und `http://payment-service:8082`). Wie Spring Boot Umgebungsvariablen auf diese Property-Namen abbildet (Stichwort Relaxed Binding), bitte in der Boot-Doku nachsehen. Die `application.yaml` mit `localhost` bleibt unverändert.
   - Ports nur an `127.0.0.1` veröffentlichen (`127.0.0.1:8080:8080` und so weiter), damit nichts im Heimnetz offen ist. Auf dem Pi kommt der öffentliche Zugang später über den Nginx Proxy Manager.
   - `restart: unless-stopped`.
   - `depends_on`: der Shop startet nach Lager und Zahlung.
4. Aufbau- und Startbefehl im `README.md`: `docker compose up --build` und `docker compose down`.
5. Alles läuft ohne Änderungen am Java-Code, die Dienste kennen weiter nur ihre Konfiguration.

## Vorgaben zur Umsetzung

- Keine Secrets oder Zugangsdaten in Dockerfile oder Compose-Datei.
- Image-Größe und Build-Zeit im Blick behalten (Maven-Cache nutzen, z. B. eine Cache-Mount in der Build-Stufe). Kein Anspruch auf Perfektion.
- Kein Healthcheck im ersten Schritt. Ein Healthcheck über `/actuator/health` braucht ein Tool im Image (`curl` oder `wget`), das ein JRE-Image oft nicht mitbringt. Erst entscheiden, wenn der Chaos-Controller Container neu startet.
- Namen der Container und Dienste so wählen, dass der Chaos-Controller sie später über die Docker-API eindeutig findet (Dienstname gleich Modulname, Projektname fest).

## Akzeptanzkriterien

- `docker compose up --build` baut alle drei Images und startet die Container ohne Fehler.
- Die `curl`-Tests aus `docs/shop-service.md` (Bestellung klappt, `409`, `404`) laufen gegen `localhost:8080` und liefern dieselben Antworten wie ohne Docker.
- `docker compose stop payment-service` gefolgt von einer Bestellung liefert `502` vom Shop, `docker compose start payment-service` macht es wieder gut.
- `mvn verify` ist weiter grün, am Java-Code hat sich nichts geändert.

## Manuell prüfen

```
docker compose up --build -d
docker compose ps
curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"productId":"watch-1","quantity":1,"totalAmount":99.90}'
docker compose stop payment-service
curl -i -X POST localhost:8080/orders -H 'Content-Type: application/json' -d '{"productId":"watch-1","quantity":1,"totalAmount":99.90}'
docker compose start payment-service
docker compose down
```

Erwartet: `201`, nach dem Stopp `502`, nach dem Start wieder `201`.

## Nicht Teil dieser Aufgabe

- `chaos-controller`, `load-generator`, Dashboard und Login
- Resilienz, zwei Shop-Instanzen
- Healthchecks, Ressourcenlimits
- Nginx Proxy Manager und die Domain `chaosplayground.cooperr.de`

## Entscheidung: Wie kommt das Projekt auf den Pi?

Der Pi holt den Code von GitHub. Entweder wird das Repo auf dem Pi geklont und mit `docker compose up --build` gestartet, oder Portainer stellt einen Stack aus dem Git-Repo bereit (Details in der Portainer-Doku nachsehen, nicht annehmen). Pipelines laufen auf GitHub. Beides ist erst für den Deployment-Schritt nötig.
