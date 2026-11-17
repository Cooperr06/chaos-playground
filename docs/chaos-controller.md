# Aufgabe: chaos-controller

Die Schaltzentrale für das Chaos: Ein Dienst mit einer kleinen Weboberfläche, über die man Latenz und Fehler in `payment-service` und `inventory-service` einstellt und deren Container stoppt, startet, pausiert und fortsetzt. Bisher ging das nur mit `curl` gegen jeden Dienst einzeln und mit `docker compose stop`. Das Live-Dashboard (Zahlen des Lastgenerators, Zustand der Circuit Breaker) und der Login kommen in eigenen Aufgaben dazu.

## Features

1. Port 8090 und `spring.mvc.problemdetails.enabled: true` in `application.yaml`. In der `pom.xml` fehlt `spring-boot-starter-validation`.
2. Konfiguration über einen `@ConfigurationProperties`-Record:
   ```
   chaos-controller:
     docker-url: http://localhost:2375
     services:
       - name: payment-service
         container: chaos-playground-payment
         url: http://localhost:8082
       - name: inventory-service
         container: chaos-playground-inventory
         url: http://localhost:8081
   ```
   - `docker-url`: Adresse der Docker-API, im Container der Socket-Proxy (siehe unten).
   - `services`: die steuerbaren Dienste mit Name, Container-Name und Basis-Adresse des Chaos-Endpunkts. Eine Liste statt einer Map, weil Umgebungsvariablen Bindestriche in Map-Schlüsseln verlieren (`payment-service` als Schlüssel ginge nicht). Achtung: Eine Liste aus einer Umgebungsvariable ersetzt die Liste aus der YAML-Datei komplett, sie wird nicht gemischt. In Compose müssen also Name, Container und Adresse jedes Dienstes gesetzt sein.
   - Nur diese Dienste lassen sich steuern, Name und Container kommen nicht über die Endpunkte. Sonst könnte jeder, der den Controller erreicht, beliebige Container stoppen.
3. `GET /services` liefert den Zustand aller Dienste: `200` mit
   ```
   [
     {"name": "payment-service", "containerState": "RUNNING", "chaos": {"latencyMs": 0, "errorRate": 0.0}},
     {"name": "inventory-service", "containerState": "EXITED", "chaos": null}
   ]
   ```
   - `containerState` aus der Docker-API (`CREATED`, `RUNNING`, `PAUSED`, `RESTARTING`, `REMOVING`, `EXITED`, `DEAD`), `UNKNOWN`, wenn Docker nicht erreichbar ist oder den Container nicht kennt.
   - `chaos` vom Chaos-Endpunkt des Dienstes, `null`, wenn der Dienst nicht antwortet (gestoppt, pausiert).
   - Ein nicht erreichbarer Dienst oder eine nicht erreichbare Docker-API ist hier kein Fehler, die Übersicht soll gerade dann funktionieren.
4. `POST /services/{name}/chaos` setzt Latenz und Fehlerquote
   - Request wie beim Chaos-Endpunkt der Dienste: `{"latencyMs": 5000, "errorRate": 0.3}`, gleiche Grenzen (0 bis 30000, 0.0 bis 1.0).
   - Erfolg: `200` mit den Einstellungen, die der Dienst zurückgibt.
   - Unbekannter Name: `404`. Ungültiger Body: `400`. Dienst nicht erreichbar: `502`.
5. `POST /services/{name}/stop`, `/start`, `/pause`, `/unpause` steuern den Container
   - Erfolg: `200` mit dem Zustand des Dienstes wie bei `GET /services`.
   - Ein schon gestoppter Container wird beim Stoppen nicht als Fehler gewertet (Docker antwortet `304`), ebenso ein schon laufender beim Starten.
   - Unbekannter Name: `404`. Docker lehnt ab (z. B. einen gestoppten Container pausieren): `409`. Docker nicht erreichbar oder anderer Fehler: `502`.
6. Timeouts: Verbindungsaufbau überall höchstens 2 Sekunden. Chaos-Endpunkte: Antwort höchstens 2 Sekunden, denn ein pausierter Container nimmt Verbindungen an, antwortet aber nie, ohne Timeout hinge der Controller. Docker-API: Antwort höchstens 15 Sekunden, weil Docker beim Stoppen bis zu 10 Sekunden auf das Beenden wartet. Den Chaos-Endpunkt eines Dienstes fragt der Controller nur, wenn der Container läuft oder sein Zustand unbekannt ist, sonst wäre jede Übersicht mit einem pausierten Dienst 2 Sekunden langsam.
7. Weboberfläche unter `/` (statisches `index.html`, ohne Framework): pro Dienst der Zustand des Containers, die Chaos-Einstellungen mit Eingabefeldern und Knöpfe für Stoppen, Starten, Pausieren und Fortsetzen. Aktualisiert sich alle 2 Sekunden über `GET /services`. Fehler der Endpunkte erscheinen als Meldung auf der Seite.

## Vorgaben zur Umsetzung

- Packages wie in den anderen Diensten unter `de.cooperr.chaos.controller`: `controller`, `service`, `client`, `dto`, `exception`, `config`. Das ergibt `de.cooperr.chaos.controller.controller`, der Name des Moduls und der Schicht fallen zusammen.
- `client/DockerClient` spricht die Docker-Engine-API über HTTP mit dem `RestClient`, ohne zusätzliche Bibliothek. Benötigt werden nur `GET /containers/{name}/json` (Zustand steht in `State.Status`) und `POST /containers/{name}/{start|stop|pause|unpause}`. Container werden über ihren Namen angesprochen, die ID ist nicht nötig. Die Pfade ohne Versionspräfix (`/v1.xx`), Docker nimmt dann die aktuelle API-Version.
- `client/ChaosClient` ruft `GET` und `POST {url}/chaos` der Dienste auf, wie der `OrderClient` im Lastgenerator mit der vollen Adresse pro Aufruf.
- `dto/ChaosSettings` ist eine Kopie des Records aus den Diensten mit denselben Validierungen (kein Modul `common`, siehe `CLAUDE.md`).
- Fehler als `ErrorResponseException`-Unterklassen wie im Shop.

## Zugriff auf Docker: Socket-Proxy

Wer den Docker-Socket hat, hat Root auf dem Host. Der Controller bekommt ihn deshalb nicht direkt, sondern spricht mit einem Socket-Proxy, der nur genau die nötigen Aufrufe durchlässt:

- Image `wollomatic/socket-proxy:1.13.2` (gibt es für arm64, amd64 und arm, also auch für den Pi). Die Erlaubnisliste sind reguläre Ausdrücke pro HTTP-Methode, `^` und `$` ergänzt der Proxy selbst. Quelle: README des Projekts, https://github.com/wollomatic/socket-proxy, gelesen am 2026-11-17.
- Erlaubt sind nur:
  - `GET /containers/chaos-playground-(payment|inventory)/json`
  - `POST /containers/chaos-playground-(payment|inventory)/(start|stop|pause|unpause)`
- `-allowfrom=chaos-controller`: Nur der Controller darf den Proxy überhaupt ansprechen.
- `-listenip=0.0.0.0`, damit der Proxy im Docker-Netz erreichbar ist. Er veröffentlicht keinen Port auf dem Host.
- Das Image läuft nicht als root, sondern als Benutzer 65534. Lesen darf es den Socket nur über die Gruppe, der der Socket gehört: `user: "65534:${DOCKER_GID:-0}"`. Die Gruppe hängt vom Host ab. Bei Docker Desktop ist es `0`, das ist der Standard (geprüft mit `stat` im Container: `0 660`). In der CI setzt der Workflow `DOCKER_GID` aus `stat -c %g /var/run/docker.sock`, auf dem Pi ist es die Gruppe `docker` (`getent group docker`). Dazu `read_only`, `cap_drop: ALL` und `no-new-privileges` wie im Beispiel der README.
- Der Socket wird nur lesend eingebunden (`/var/run/docker.sock:/var/run/docker.sock:ro`). Das schränkt die API nicht ein, verhindert aber, dass der Proxy die Socket-Datei selbst verändert.

## Docker Compose und CI

- `chaos-controller/Dockerfile` wie beim Lastgenerator, Port 8090.
- In `docker-compose.yml`:
  - `socket-proxy`, Container `chaos-playground-socket-proxy`, mit den Einstellungen oben.
  - `chaos-controller`, Container `chaos-playground-controller`, Port `127.0.0.1:8090:8090`, Adressen per Umgebungsvariable (`CHAOSCONTROLLER_DOCKERURL: http://socket-proxy:2375`, für beide Dienste `CHAOSCONTROLLER_SERVICES_<i>_NAME`, `_CONTAINER` und `_URL` mit den Adressen im Docker-Netz), `depends_on` auf Proxy, Zahlung und Lager.
- CI, Job `images`: `chaos-controller` kommt in die Matrix.
- CI, Job `smoke-test`:
  - Warten auch auf Port 8090.
  - Chaos über den Controller: `{"latencyMs":0,"errorRate":1.0}` an `payment-service`, Bestellung an `shop-plain` liefert `502`, danach zurücksetzen.
  - Container über den Controller: `inventory-service` stoppen, `GET /services` zeigt `EXITED`, Bestellung an `shop-plain` liefert `502`. Danach starten, auf den Health-Check von 8081 warten, Bestellung liefert wieder `201`.

## Tests (Akzeptanzkriterien)

- `DockerClientTest` mit `MockRestServiceServer`: Zustand lesen, Zustand bei Fehler `UNKNOWN`, Aktion schickt den richtigen `POST`, `304` gilt als Erfolg, `409` und `5xx` werden zu den passenden Exceptions.
- `ChaosClientTest` mit `MockRestServiceServer`: lesen, setzen, nicht erreichbar.
- `ChaosControlServiceTest` mit gemockten Clients: Übersicht auch bei nicht erreichbarem Dienst, unbekannter Name, Aktion liefert den neuen Zustand.
- `ServiceControllerTest` mit `@WebMvcTest`: alle Endpunkte mit Erfolg, `400`, `404`, `409`, `502`.
- `mvn -o verify` im Root ist grün, der Smoke-Test in der CI auch.

## Manuell prüfen

```
docker compose up --build -d
curl -s -w '\n' localhost:8090/services
curl -s -w '\n' -X POST localhost:8090/services/payment-service/chaos -H 'Content-Type: application/json' -d '{"latencyMs":3000,"errorRate":0}'
curl -s -w '\n' -X POST localhost:8090/services/inventory-service/pause
curl -s -w '\n' localhost:8090/services
curl -s -w '\n' -X POST localhost:8090/services/inventory-service/unpause
docker compose down
```

Danach `http://localhost:8090` im Browser öffnen und dasselbe über die Knöpfe machen.

## Nicht Teil dieser Aufgabe

- Live-Dashboard mit Zahlen des Lastgenerators und Zustand der Circuit Breaker (Server-Sent Events).
- Login mit `viewer` und `admin` (Spring Security). Bis dahin ist der Controller nur über `127.0.0.1` erreichbar.
- Lastgenerator über den Controller steuern.
- Zweite Lager-Instanz (Szenario 4).
