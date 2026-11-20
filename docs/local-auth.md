# Aufgabe: Lokale Testumgebung mit Traefik und Authelia

Auf dem Pi stehen vor dem Chaos Playground Traefik und Authelia (siehe `docs/login.md`). Damit Login, Rollen und Routing schon vor dem fertigen Homelab testbar sind, gibt es lokal denselben Aufbau: eine zweite Compose-Datei mit Traefik und Authelia, echte Hostnamen unter `cooperr.de`, TLS mit dem selbst erzeugten Zertifikat von Traefik. Unterschiede zum Pi: Hostnamen zeigen per DNS auf `127.0.0.1`, selbst erzeugtes Zertifikat statt Let's Encrypt, Benutzer aus einer Datei.

## Hostnamen

| Lokal | Pi | Ziel |
|---|---|---|
| `chaosplayground.local.cooperr.de` | `chaosplayground.cooperr.de` | Controller, unter `/grafana` Grafana |
| `auth.local.cooperr.de` | wird im Homelab festgelegt | Anmeldeportal von Authelia |

Warum nicht `localhost`: Authelia verlangt für das Session-Cookie eine Domain mit Punkt, die keine Spezialdomain wie `localhost` ist, und HTTPS (https://www.authelia.com/configuration/session/introduction/, gelesen am 2026-11-20: "Must include at least one domain segment i.e. period.", "Must not be a special use domain like `localhost`", "(the use of TLS is a strict requirement)"). Cookie-Domain ist `local.cooperr.de`, sie deckt beide Hostnamen ab.

## Aufbau

- `docker-compose.yml` bleibt unverändert und läuft weiter allein (CI, schneller Start ohne Login).
- Neue Datei `docker-compose.auth.yml` ergänzt Traefik und Authelia und setzt die öffentlichen Adressen für Controller und Grafana. Start mit beiden Dateien:
  ```
  docker compose -f docker-compose.yml -f docker-compose.auth.yml up --build
  ```
  In der IntelliJ-Run-Configuration für Docker Compose beide Dateien eintragen (eigene Configuration, z. B. "compose with auth").
- Konfiguration unter `local-auth/`:
  ```
  local-auth/
    traefik/traefik.yml          statische Konfiguration
    traefik/dynamic/chaos.yml    Router, Middleware, Services
    authelia/configuration.yml   Authelia
    authelia/users_database.yml  Testbenutzer (nicht im Repo)
    secrets/                     Secrets von Authelia (nicht im Repo)
  ```
- `.gitignore`: `local-auth/secrets/`, `local-auth/authelia/users_database.yml`.

## Features

### Traefik

1. Image `traefik:v3.7.19` (Docker Hub, arm64 vorhanden, geprüft am 2026-11-20; Authelia nennt v3.7.13 als getestet). Container-Name `chaos-playground-traefik`, Port `127.0.0.1:443:443`, Volume `./local-auth/traefik` nach `/etc/traefik` (`:ro`). Kein Docker-Socket: Traefik liest die Routen aus Dateien (File-Provider), nicht aus Docker-Labels.
2. `traefik.yml` (statisch): ein Entrypoint `websecure` auf `:443`, File-Provider auf das Verzeichnis mit den dynamischen Dateien:
   ```
   entryPoints:
     websecure:
       address: ":443"
   providers:
     file:
       directory: /etc/traefik/dynamic
   ```
   Quelle File-Provider: https://doc.traefik.io/traefik/reference/install-configuration/providers/others/file/ ("It is recommended to use directory.").
3. `dynamic/chaos.yml`, Middleware für Authelia:
   ```
   http:
     middlewares:
       authelia:
         forwardAuth:
           address: http://authelia:9091/api/authz/forward-auth
           authResponseHeaders:
             - Remote-User
             - Remote-Groups
   ```
   - `authResponseHeaders` überschreibt gleichnamige Header des Clients: "set on forwarded request, replacing any existing conflicting headers" (https://doc.traefik.io/traefik/reference/routing-configuration/http/middlewares/forwardauth/).
   - Das Beispiel von Authelia setzt zusätzlich `trustForwardHeader: true`. Weggelassen: Traefik markiert die Option als veraltet ("deprecated and will be removed in the next major version"), und ohne sie setzt Traefik die `X-Forwarded-*`-Header für Authelia selbst, statt denen des Clients zu glauben.
4. Router in derselben Datei, alle mit `entryPoints: [websecure]` und `tls: {}`:
   - `chaos`: Regel ``Host(`chaosplayground.local.cooperr.de`) && !PathPrefix(`/actuator`) && !PathPrefix(`/grafana`)``, Middleware `authelia`, Service `chaos-controller` (`http://chaos-controller:8090`).
   - `grafana`: Regel ``Host(`chaosplayground.local.cooperr.de`) && PathPrefix(`/grafana`)``, Middleware `authelia`, Service `grafana` (`http://grafana:3000`).
   - `authelia`: Regel ``Host(`auth.local.cooperr.de`)``, ohne Middleware, Service `authelia` (`http://authelia:9091`) mit eigenem `serversTransport` (`forwardingTimeouts.idleConnTimeout: 5s`, Standard `90s`). Grund: Authelia bricht Verbindungen ab, auf denen nach `server.timeouts.read` (Standard 6 s, `internal/configuration/schema/server.go`, v4.39.28) noch keine Anfrage kam, und loggt dafür je ein `Request timeout occurred while handling request from client.` mit `408`. Traefik öffnet beim Laden des Portals mehr Verbindungen als nötig und hielte die ungenutzten 90 s offen. Mit 5 s schließt Traefik sie vorher. Leerlauf nach einer beantworteten Anfrage schließt Authelia dagegen ohne Log (fasthttp v1.74.0, `server.go`).
   - `/actuator` passt auf keinen Router, Traefik antwortet `404`. Damit ist von außen nur das Dashboard erreichbar.
   - Warum `!PathPrefix(`/grafana`)` in der Regel von `chaos`: Ohne eigene Priorität gewinnt bei Traefik die längere Regel ("The priority is directly equal to the length of the rule", https://doc.traefik.io/traefik/reference/routing-configuration/http/routing/rules-and-priority/). Die Regel von `chaos` ist länger als die von `grafana` und würde `/grafana` sonst schlucken.
   - Form eines Routers und Services (Traefik-Doku zum File-Provider):
     ```
     http:
       routers:
         grafana:
           rule: Host(`chaosplayground.local.cooperr.de`) && PathPrefix(`/grafana`)
           entryPoints:
             - websecure
           middlewares:
             - authelia
           service: grafana
           tls: {}
       services:
         grafana:
           loadBalancer:
             servers:
               - url: http://grafana:3000
     ```
5. Ein selbst signiertes Zertifikat für `*.local.cooperr.de`, einmal pro Rechner mit `openssl` erzeugt (README), liegt git-ignoriert unter `local-auth/traefik/certs/` und ist in `chaos.yml` als `tls.stores.default.defaultCertificate` eingetragen. Der Browser warnt, die Warnung wird pro Hostname weggeklickt. Ohne eigenes Zertifikat nähme Traefik sein erzeugtes ("If no `defaultCertificate` is provided, Traefik will use the generated one.", https://doc.traefik.io/traefik/reference/routing-configuration/http/tls/tls-certificates/), das bei jedem Start neu entsteht, und die Ausnahme im Browser gälte nach jedem Neustart nicht mehr. Das eigene Zertifikat kommt in keinen Schlüsselbund und braucht keine Adminrechte.
   - Warum nicht `mkcert`: `mkcert -install` trägt eine eigene Zertifizierungsstelle mit Adminrechten in den System-Schlüsselbund ein, ihr privater Schlüssel liegt unverschlüsselt im Benutzerverzeichnis. Wer ihn bekommt, kann für diesen Rechner gültige Zertifikate für jede Domain ausstellen. Auf einem Arbeitsrechner nicht vertretbar.
   - Warum nicht Let's Encrypt per DNS-Challenge: INWX (DNS von `cooperr.de`) bietet laut lego-Doku nur Benutzername und Passwort des Kontos an (https://go-acme.github.io/lego/dns/inwx/), die vollen Zugangsdaten zur Domain lägen dann auf jedem Rechner.
   - Voraussetzung: `https://cooperr.de` setzt kein HSTS mit `includeSubDomains`, sonst lässt der Browser die Warnung nicht wegklicken (Stand 2026-11-20: unter `cooperr.de` antwortet kein HTTPS-Server).

### Authelia

- Redis für die Sitzungen: Image `redis:8.8.4-alpine` (Docker Hub, arm64 vorhanden, geprüft am 2026-11-20), Container `chaos-playground-redis`, Befehl `redis-server --save 60 1` (Schnappschuss spätestens 60 s nach einer Änderung und beim regulären Stoppen), benanntes Volume `authelia-sessions` auf `/data`, damit die Sitzungen Neustarts und `docker compose down` überstehen (erst `down -v` löscht sie). Eigenes Netz `auth`, in dem nur Authelia und Redis hängen, kein Port: Wer Redis lesen kann, kann sich als jeder angemeldete Benutzer ausgeben. Deshalb lokal kein Passwort, die anderen Dienste kommen nicht hin.

6. Image `authelia/authelia:4.39.28` (Docker Hub, arm64 vorhanden, geprüft am 2026-11-20). Container-Name `chaos-playground-authelia`, kein veröffentlichter Port. Volumes: `./local-auth/authelia` nach `/config` (`:ro`), `./local-auth/secrets` nach `/secrets` (`:ro`). Für Datenbank und Benachrichtigungen `tmpfs: /data:uid=1000,gid=1000` (sonst gehört es root und Authelia darf nicht schreiben), beides wird lokal nicht gebraucht und darf beim Neustart weg. Dazu `user: "1000:1000"`: Als root ruft das Startskript des Images (`entrypoint.sh` im Authelia-Repo) zuerst `chown -R` auf `/config` auf, das scheitert am schreibgeschützten Volume. Mit einem anderen Benutzer startet es Authelia direkt. Die Konfiguration erwartet das Image unter `/config/configuration.yml` (`X_AUTHELIA_CONFIG` im Dockerfile).
7. Secrets per Datei, nicht in `configuration.yml` (https://www.authelia.com/configuration/methods/secrets/). Umgebungsvariablen im Compose-Service:
   ```
   AUTHELIA_SESSION_SECRET_FILE: /secrets/session_secret
   AUTHELIA_STORAGE_ENCRYPTION_KEY_FILE: /secrets/storage_encryption_key
   AUTHELIA_IDENTITY_VALIDATION_RESET_PASSWORD_JWT_SECRET_FILE: /secrets/jwt_secret
   ```
   Den jeweiligen Schlüssel dann nicht auch in `configuration.yml` setzen, sonst startet Authelia nicht ("Authelia will not start if you define a secret as well as any other configuration method.").
8. `configuration.yml` (Schlüssel aus `config.template.yml` im Authelia-Repo und den Seiten zu Session, Storage und Access Control):
   ```
   server:
     address: 'tcp://:9091/'
   authentication_backend:
     file:
       path: '/config/users_database.yml'
   session:
     cookies:
       - domain: 'local.cooperr.de'
         authelia_url: 'https://auth.local.cooperr.de'
     redis:
       host: 'redis'
       port: 6379
   storage:
     local:
       path: '/data/db.sqlite3'
   notifier:
     filesystem:
       filename: '/data/notification.txt'
   access_control:
     default_policy: 'deny'
     rules:
       - domain: 'chaosplayground.local.cooperr.de'
         policy: 'one_factor'
         subject:
           - 'group:chaos-viewer'
           - 'group:chaos-admin'
   ```
   - `session.redis`: Ohne Redis hält Authelia die Sitzungen im Arbeitsspeicher ("By default Authelia uses an in-memory provider.", https://www.authelia.com/configuration/session/redis/), jeder Neustart meldet dann alle ab, auch mit "Angemeldet bleiben".
   - `one_factor`: Benutzername und Passwort, keine zweite Stufe. Für Prof und Kollegen reicht das.
   - `subject` als Liste heißt "eine der Gruppen" (oberste Ebene der Liste ist ODER). Wer in keiner der Gruppen ist, wird von Authelia abgewiesen und kommt gar nicht erst beim Controller an.
   - Keine Regel mit `policy: 'bypass'` für diese Domain: Bei `bypass` schickt Authelia keine eigenen `Remote-*`-Header, dann kämen gefälschte Header des Clients durch.
9. `users_database.yml` mit zwei Testbenutzern, Format aus https://www.authelia.com/reference/guides/passwords/:
   ```
   users:
     admin:
       displayname: 'Test Admin'
       password: '<argon2-Hash>'
       email: 'admin@local.cooperr.de'
       groups:
         - 'chaos-admin'
     viewer:
       displayname: 'Test Viewer'
       password: '<argon2-Hash>'
       email: 'viewer@local.cooperr.de'
       groups:
         - 'chaos-viewer'
   ```
   Hash erzeugen (gleiche Seite, mit festem Tag statt `latest`):
   ```
   docker run --rm authelia/authelia:4.39.28 authelia crypto hash generate argon2 --password 'admin'
   docker run --rm authelia/authelia:4.39.28 authelia crypto hash generate argon2 --password 'viewer'
   ```
   Passwort gleich Benutzername, nur lokal.
   Die Ausgabe beginnt mit `Digest: `, in die Datei kommt der Teil danach (`$argon2id$...`).

### Compose

10. `docker-compose.auth.yml` enthält die Dienste `traefik` und `authelia` und überschreibt:
    - `chaos-controller`: `CHAOSCONTROLLER_GRAFANAURL: https://chaosplayground.local.cooperr.de/grafana`, `CHAOSCONTROLLER_LOGOUTURL: https://auth.local.cooperr.de/logout`
    - `grafana`: `GF_SERVER_ROOT_URL: https://chaosplayground.local.cooperr.de/grafana/`
    Compose führt beide Dateien zusammen, gleichnamige Umgebungsvariablen aus der zweiten Datei gewinnen.
11. Die Ports der Dienste auf `127.0.0.1` aus `docker-compose.yml` bleiben, `curl` direkt auf `8090` funktioniert weiter (siehe `docs/login.md`).

## Einmalige Einrichtung auf dem Mac

Kein Zertifikat, keine Adminrechte nötig. Die Hostnamen brauchen keine Einrichtung: Im DNS von `cooperr.de` zeigt der Wildcard-Eintrag `*.local.cooperr.de` auf `127.0.0.1` (geprüft am 2026-11-20 mit `dig chaosplayground.local.cooperr.de`). So braucht niemand `sudo` für `/etc/hosts`. Manche Router blockieren DNS-Antworten mit privaten Adressen (DNS-Rebind-Schutz, z. B. FritzBox), dort eine Ausnahme für `local.cooperr.de` eintragen.

In dieser Reihenfolge:

1. Secrets erzeugen:
   ```
   mkdir -p local-auth/secrets
   for name in session_secret storage_encryption_key jwt_secret; do openssl rand -hex 32 > local-auth/secrets/$name; done
   ```
2. Zwei Hashes erzeugen (Befehl oben) und `local-auth/authelia/users_database.yml` anlegen.

Danach: `docker compose -f docker-compose.yml -f docker-compose.auth.yml up --build`.

## Akzeptanzkriterien

Voraussetzung: `docs/login.md` ist umgesetzt.

- `docker compose ... up` startet ohne Fehler, im Log von Authelia kein Konfigurationsfehler.
- `https://chaosplayground.local.cooperr.de` im Browser leitet nach dem Wegklicken der Zertifikatswarnung auf `https://auth.local.cooperr.de` um (dort noch einmal wegklicken). Nach dem Login als `viewer` erscheint das Dashboard mit Grafana, oben "Nur ansehen", Knöpfe deaktiviert.
- Als `viewer` in der Konsole der Entwicklertools:
  ```
  fetch('/me', {headers: {'Remote-Groups': 'chaos-admin'}}).then(r => r.json()).then(console.log)
  ```
  liefert `admin: false`. Damit ist belegt, dass Traefik den gefälschten Header durch den von Authelia ersetzt.
- Abmelden über den Namen unten in der Sidebar führt nach etwa 2 Sekunden zurück zur Anmeldung, nach dem Login wieder zum Dashboard.
- Abmelden in einem anderen Tab, während das Dashboard offen ist: Grafana zeigt kurz einen Fehler, spätestens nach 2 Sekunden lädt die Seite neu und landet bei der Anmeldung.
- Nach Abmelden und Login als `admin`: Knöpfe aktiv, Latenz setzen und Container stoppen klappt.
- Ohne Login, aus dem Terminal:
  ```
  curl -sk -o /dev/null -w '%{http_code}\n' https://chaosplayground.local.cooperr.de/services -H 'Remote-User: x' -H 'Remote-Groups: chaos-admin'
  curl -sk -o /dev/null -w '%{http_code}\n' https://chaosplayground.local.cooperr.de/actuator/health
  ```
  Erwartet: eine Umleitung (`302` oder `303`) statt `200`, dann `404`. `-k`, weil `curl` dem selbst erzeugten Zertifikat nicht traut.
- `docker compose up` nur mit `docker-compose.yml` läuft weiter wie bisher, die CI bleibt grün.

## Nicht Teil dieser Aufgabe

- Traefik und Authelia auf dem Pi, das richtet das Homelab ein (Briefing außerhalb des Repos).
- HTTP auf Port 80 mit Umleitung auf HTTPS. Lokal wird direkt `https://` aufgerufen.
- Zwei-Faktor-Anmeldung, Passwort-Reset per Mail.
