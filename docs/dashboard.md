# Aufgabe: Dashboard mit Prometheus und Grafana

Zeigt, was die Störungen anrichten: Wie viele Bestellungen durchkommen, wie lange sie dauern, wie viele Threads im Shop belegt sind und in welchem Zustand der Circuit Breaker ist. Beide Shops im Vergleich, mit wählbarem Zeitraum, Durchschnitt und Perzentilen. Die Graphen erscheinen auf der Seite des `chaos-controller` unter einem neuen Regler für die Last.

## Entscheidung: Prometheus und Grafana statt eigener Diagramme

- Zeitraum wählen, Zoomen, Durchschnitt und Perzentile bringt Grafana mit, selbst gebaut wäre das viel Arbeit für ein schlechteres Ergebnis. Prometheus und Grafana sind der Standard in der Praxis.
- Prometheus holt die Metriken alle 5 Sekunden von `/actuator/prometheus` jedes Dienstes. Grafana fragt Prometheus und aktualisiert sich selbst alle 5 Sekunden, die im README geplanten Server-Sent Events entfallen.
- Grafana ist in die Seite des Controllers eingebettet (iframe) und erlaubt anonymen Lesezugriff. Auf dem Pi ist Grafana deshalb neben dem Controller über den Nginx Proxy Manager erreichbar, unter `/grafana`, nur lesend.
- Images: `prom/prometheus:v3.15.1` und `grafana/grafana:13.2.5`, beide für arm64 vorhanden (Docker Hub, geprüft am 2026-11-17).

## Features

### Metriken in allen Diensten

1. Jeder Dienst bekommt `io.micrometer:micrometer-registry-prometheus` (Version kommt über das BOM von Spring Boot, 1.17.1) und gibt den Endpunkt `prometheus` frei (`management.endpoints.web.exposure.include`). Damit kommen ohne eigenen Code die Standardmetriken heraus: HTTP-Anfragen, JVM, im Shop mit Resilienz zusätzlich `resilience4j_circuitbreaker_state` (über `resilience4j-micrometer`, das `resilience4j-spring-boot4` mitbringt).
2. Im Shop `server.tomcat.mbeanregistry.enabled: true`, sonst fehlen die Thread-Metriken (`tomcat_threads_busy_threads`, `tomcat_threads_config_max_threads`). Sie zeigen den Kaskadenfehler aus Szenario 1: Beim Shop ohne Schutz sind alle 20 Threads belegt.
   - Actuator läuft im Shop auf einem eigenen Port (`management.server.port: 9080`) mit eigenem Connector und eigenen Threads. Im ersten Test stand Prometheus beim Shop ohne Schutz sonst selbst in der Warteschlange (`up` = 0), und das Panel der Threads war genau im Kaskadenfehler leer. Die Thread-Metriken gibt es danach für beide Connectoren, die Abfragen filtern auf `name="http-nio-8080"`.
   - Host-Ports: `127.0.0.1:9080` für `shop-plain`, `127.0.0.1:9083` für `shop-resilient`. Der Smoke-Test wartet dort auf den Health-Check.

### Metriken des Lastgenerators

3. Jede Bestellung wird als Timer `loadgenerator.orders` gezählt, mit den Tags
   - `target`: Hostname des Ziels, z. B. `shop-plain`,
   - `result`: der Statuscode (`201`, `202`, `502`), `timeout` oder `connection_error`.
   - Mit Perzentil-Histogramm, damit Grafana p95 und p99 über beliebige Zeiträume berechnen kann.
4. Gauge `loadgenerator.orders.in.flight` mit Tag `target`: Anfragen, auf deren Antwort der Lastgenerator gerade wartet.
5. Die Metriken laufen unabhängig von den Zählern hinter `GET /load` weiter, sie werden beim Ändern der Rate nicht zurückgesetzt (Prometheus rechnet mit fortlaufenden Zählern).

### Last-Regler im Controller

6. `GET /load` und `POST /load` im Controller reichen an den Lastgenerator weiter, Request und Response `{"ratePerSecond": 10}`, gleiche Grenzen (0 bis 100). Lastgenerator nicht erreichbar: `502`.
7. Konfiguration `chaos-controller.load-generator-url` (Standard `http://localhost:8091`).

### Einbetten von Grafana

8. `GET /config` im Controller liefert `{"grafanaUrl": "http://localhost:3000/grafana"}`. Die Seite baut daraus die Adresse des iframes. Konfiguration `chaos-controller.grafana-url`: lokal die Adresse auf dem Host, auf dem Pi `/grafana` (gleiche Domain).
9. Aufbau der Seite: links eine Sidebar mit allen Steuerelementen (Bestellungen pro Sekunde, darunter ein gemeinsamer Block "Dienste" mit Zahlung und Lager), rechts Grafana über die volle Höhe. Die Seite selbst scrollt nicht, nur Sidebar oder Grafana, wenn sie nicht passen. Die Sidebar lässt sich wegklappen, der Zustand liegt im `localStorage` des Browsers. Auf schmalen Bildschirmen steht die Sidebar oben.
   - Grafana läuft im iframe im Kiosk-Modus (`&kiosk`): Menü, Suche und "Sign in" sind weg, Zeitraum und Aktualisierung bleiben (geprüft mit Grafana 13.2.5).

### Grafana-Dashboard

10. Datenquelle und Dashboard werden beim Start aus Dateien geladen (Provisioning), nichts wird in der Oberfläche geklickt. Ablage unter `monitoring/`.
11. Panels, jeweils beide Shops im Vergleich:
    - Anteil beantworteter Bestellungen (`201` und `202`, auch "Zahlung wird nachgeholt" ist eine Antwort für die Kundschaft).
    - Ergebnisse pro Sekunde, gestapelt nach Shop und Ergebnis.
    - Antwortzeit: Durchschnitt und p95.
    - Offene Anfragen (`loadgenerator_orders_in_flight`).
    - Belegte Threads im Shop mit dem Maximum als Linie.
    - Aufrufe der Zahlung im Shop mit Schutz pro Sekunde: erfolgreich, fehlgeschlagen, abgewiesen (`resilience4j_circuitbreaker_calls_seconds_count`, `resilience4j_circuitbreaker_not_permitted_calls_total`).
    - Zustand des Circuit Breakers im Shop mit Schutz als Zeitleiste über die ganze Breite (geschlossen, halb offen, offen). Halb offen dauert oft nur etwa eine Sekunde (3 Probeaufrufe mit Timeout 1 s) und ist bei Abfragen alle 5 Sekunden nur manchmal als schmaler Streifen zu sehen, deshalb das Panel mit den Aufrufen daneben.
12. Standardzeitraum 15 Minuten, Aktualisierung alle 5 Sekunden, Raten über `$__rate_interval`. Panels so niedrig, dass das Dashboard bei 1440 x 900 ohne Scrollen passt.
    - Die Zeitleiste des Circuit Breakers braucht die feste Farbe statt "From thresholds", sonst gruppiert Grafana nach Schwellwerten und ignoriert die Zuordnung der Werte zu "geschlossen", "halb offen" und "offen".
13. Grafana: anonymer Zugriff nur mit der Rolle `Viewer`, Login-Formular und Basic Auth abgeschaltet (es gibt nichts zu verwalten, alles kommt aus Dateien), Einbetten erlaubt, Telemetrie und Update-Prüfung aus, ausgeliefert unter dem Unterpfad `/grafana`.

## Docker Compose und CI

- `prometheus`, Container `chaos-playground-prometheus`, Port `127.0.0.1:9090:9090`, Konfiguration aus `monitoring/prometheus/prometheus.yml`. Ein Job pro Dienst, der Jobname unterscheidet die beiden Shops. Daten nur im Arbeitsspeicher (`tmpfs`, 256 MB), höchstens 1 Tag und 200 MB: Bei jedem Start leer, nichts bleibt auf der Platte liegen, schont die SD-Karte des Pi.
- `grafana`, Container `chaos-playground-grafana`, Port `127.0.0.1:3000:3000`, Provisioning aus `monitoring/grafana/`. `GRAFANA_ROOT_URL` als Umgebungsvariable mit Standard `http://localhost:3000/grafana/`, auf dem Pi die öffentliche Adresse. Grafanas eigene Datenbank (`/var/lib/grafana`) liegt ebenfalls im Arbeitsspeicher, Datenquelle und Dashboard kommen bei jedem Start aus den Dateien. Das `tmpfs` braucht die Option `exec`: Die Datenquelle Prometheus ist in Grafana 13 ein eigenes Programm unter `/var/lib/grafana/plugins`, und Docker verbietet in einem `tmpfs` sonst das Ausführen (Fehler "Plugin failed to start", alle Panels ohne Daten).
- Controller: `CHAOSCONTROLLER_LOADGENERATORURL: http://load-generator:8091`, `CHAOSCONTROLLER_GRAFANAURL` mit Standard `http://localhost:3000/grafana`.
- CI, Job `smoke-test`:
  - Warten auf Prometheus (`/-/ready`) und Grafana (`/grafana/api/health`).
  - Prometheus erreicht alle 6 Dienste (`count(up == 1)` ist 6).
  - Grafana liefert das Dashboard an anonyme Besucher aus (`/grafana/api/dashboards/uid/chaos-playground` mit `200`).
  - Nach dem Lastgenerator-Schritt kennt Prometheus `loadgenerator_orders_seconds_count` für beide Shops.
  - Last über den Controller setzen und lesen.

- Logs aller Container höchstens 2 Dateien zu 5 MB (`x-logging` in `docker-compose.yml`). Docker begrenzt sie sonst nicht, im Dauerbetrieb auf dem Pi wüchsen sie über Wochen.

## Tests (Akzeptanzkriterien)

- `OrderMetricsTest`: Timer mit den richtigen Tags, Ergebnis für Timeout und Verbindungsfehler, Gauge für offene Anfragen.
- `LoadServiceTest`: zählt weiter wie bisher, zusätzlich mit `SimpleMeterRegistry`.
- `LoadClientTest`, `LoadControllerTest` und `ConfigControllerTest` im Controller.
- `mvn -o verify` im Root ist grün, der Smoke-Test in der CI auch.

## Manuell prüfen

```
docker compose up --build -d
```

`http://localhost:8090` öffnen, Last auf 10 stellen, bei der Zahlung 5000 ms Verzögerung setzen. In den Graphen: Beim Shop ohne Schutz steigen offene Anfragen und belegte Threads bis 20, der Anteil beantworteter Bestellungen fällt. Beim Shop mit Schutz springt der Circuit Breaker auf offen, Antwortzeit bleibt niedrig. Zeitraum oben rechts im Dashboard umstellen.

## Nicht Teil dieser Aufgabe

- Login mit `viewer` und `admin`. Bis dahin sind Controller und Grafana nur über `127.0.0.1` erreichbar.
- Alarme in Grafana.
- Logs oder Traces (Loki, Tempo).
