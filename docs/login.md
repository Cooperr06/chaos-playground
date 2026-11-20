# Aufgabe: Login über Authelia

Auf dem Pi sind der `chaos-controller` und Grafana öffentlich erreichbar, beide hinter Traefik und Authelia (siehe `CLAUDE.md`). Der Controller bekommt kein eigenes Login-Formular. Er liest, wer angemeldet ist, aus Headern, die Traefik nach der Prüfung durch Authelia mitschickt, und entscheidet selbst, wer die Chaos-Knöpfe bedienen darf. Wie die Umgebung lokal aussieht, beschreibt `docs/local-auth.md`.

## Ablauf (Forward-Auth)

1. Der Browser ruft `https://chaosplayground.cooperr.de/...` auf. Traefik fragt vor jeder Anfrage bei Authelia nach (`/api/authz/forward-auth`, im Docker-Netz, nicht über den Browser).
2. Keine gültige Session: Authelia leitet auf das eigene Anmeldeportal um, danach zurück zur aufgerufenen Seite.
3. Gültige Session: Traefik reicht die Anfrage an den Controller weiter und setzt dabei die Header aus der Antwort von Authelia, unter anderem `Remote-User` (Benutzername) und `Remote-Groups` (Gruppen, durch Komma getrennt, z. B. `chaos-admin,dev`).

Quellen, gelesen am 2026-11-20 (Stand der Seiten 2026-09-25):

- https://www.authelia.com/integration/proxies/traefik/: "Traefik utilizes the ForwardAuth Authz implementation." Im Beispiel `forwardAuth.address: 'http://authelia:9091/api/authz/forward-auth'` und `authResponseHeaders` mit `Remote-User`, `Remote-Groups`, `Remote-Email`, `Remote-Name`.
- https://www.authelia.com/integration/trusted-header-sso/introduction/: Tabelle der Header, Beispiel für `Remote-Groups` ist `admin,dev`. Außerdem: "your proxy must ensure only Authelia is setting these headers".

Grafana braucht keine Änderung: Traefik schützt `/grafana` mit derselben Middleware, Grafana selbst bleibt anonym und nur lesend.

## Rollen

| Gruppe in Authelia | Rolle im Controller | Darf |
|---|---|---|
| `chaos-viewer` | `VIEWER` | Seite, Dashboard und alle `GET`-Endpunkte |
| `chaos-admin` | `ADMIN` | zusätzlich Chaos setzen, Container steuern, Last ändern |

Wer in beiden Gruppen ist, ist `ADMIN`. Andere Gruppen werden ignoriert.

Die Gruppennamen sind einstellbar, damit das Homelab auch schon bestehende Gruppen verwenden kann: `chaos-controller.auth.admin-group` und `chaos-controller.auth.viewer-group` (Record `AuthProperties`), Standard in `application.yaml` sind die Namen aus der Tabelle. In Compose als `CHAOSCONTROLLER_AUTH_ADMINGROUP` und `CHAOSCONTROLLER_AUTH_VIEWERGROUP`. Header- und Rollennamen bleiben Konstanten, sie sind durch Authelia bzw. den Code festgelegt.

## Features

1. Abhängigkeiten in der `pom.xml` des Controllers: `spring-boot-starter-security` und (Scope `test`) `spring-boot-starter-security-test`, Versionen kommen aus dem BOM von Boot 4.1.1 (Spring Security 7.1.1). Danach in IntelliJ Maven neu laden, das lädt die Jars nach `~/.m2`.
2. Anmeldung aus den Headern: `Remote-User` ist der Name, `Remote-Groups` wird am Komma getrennt, Leerzeichen um die Namen werden entfernt, daraus entstehen die Rollen nach der Tabelle oben. Fehlt `Remote-User` oder `Remote-Groups`, ist niemand angemeldet.
3. Regeln, in dieser Reihenfolge:
   - `GET /actuator/health` und `GET /actuator/prometheus`: ohne Anmeldung. Prometheus und der Smoke-Test der CI rufen den Controller direkt im Docker-Netz auf, ohne Traefik und ohne Header. Von außen ist `/actuator` gesperrt, das macht Traefik (siehe `docs/local-auth.md`): Öffentlich erreichbar ist nur das Dashboard.
   - `POST /services/**` und `POST /load`: nur `ADMIN`.
   - Alle anderen `GET`-Anfragen (Seite, statische Dateien, `/config`, `/services`, `/load`, `/me`): `VIEWER` oder `ADMIN`.
   - Alles andere: abgelehnt.
4. Statuscodes: Nicht angemeldet: `401`. Angemeldet, aber ohne passende Rolle: `403`. Der Body ist egal, `app.js` zeigt dann ihren Standardtext "antwortet mit 403".
5. Keine HTTP-Session, kein Login-Formular, kein HTTP Basic, kein Logout in Spring (abgemeldet wird bei Authelia). Jede Anfrage bringt ihre Header mit.
6. CSRF-Schutz bleibt an (Begründung unten unter "Warum CSRF"). Spring legt ein zufälliges Token in das Cookie `XSRF-TOKEN`, `app.js` schickt es bei jedem `POST` im Header `X-XSRF-TOKEN` zurück. Ohne passenden Header lehnt Spring den `POST` mit `403` ab, auch für `ADMIN`.
7. `GET /me` liefert den angemeldeten Benutzer: `200` mit `{"name": "prof", "admin": true}`.
8. Oberfläche: Oben steht, wer angemeldet ist und mit welcher Rolle ("Admin" oder "Nur ansehen"). Für `VIEWER` sind die Eingabefelder und Knöpfe für Chaos, Container und Last deaktiviert. Das ist nur Bequemlichkeit, die Prüfung macht der Server.

## Vorgaben zur Umsetzung

Alle Klassen unten sind im Quelltext von Spring Security 7.1.1 bzw. Spring Boot 4.1.1 nachgesehen.

### `config/SecurityConfig`

`@Configuration` mit einer `@Bean SecurityFilterChain securityFilterChain(HttpSecurity http)`. Header- und Gruppennamen als Konstanten.

**Header lesen** (Package `org.springframework.security.web.authentication.preauth`):

- `RequestHeaderAuthenticationFilter` liest die Header:
  - `setPrincipalRequestHeader("Remote-User")`: wird der Name.
  - `setCredentialsRequestHeader("Remote-Groups")`: der Inhalt kommt als "Credentials" mit, darüber erreichen die Gruppen den nächsten Schritt.
  - `setExceptionIfHeaderMissing(false)`: Ohne Header gilt die Anfrage als nicht angemeldet statt als Fehler.
  - `setSecurityContextRepository(new RequestAttributeSecurityContextRepository())` (Package `org.springframework.security.web.context`): Der Standard speichert die Anmeldung in einer HTTP-Session, die wollen wir nicht.
  - `setAuthenticationManager(new ProviderManager(provider))` mit dem Provider unten.
  - Den Filter im Methodenrumpf mit `new` erzeugen, nicht als eigene `@Bean`: Spring Boot registriert jede Filter-Bean zusätzlich als normalen Servlet-Filter, dann liefe er doppelt.
- `PreAuthenticatedAuthenticationProvider` macht aus Name und Gruppen einen Benutzer. Er bekommt mit `setPreAuthenticatedUserDetailsService(...)` ein `AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken>` (Interface aus `org.springframework.security.core.userdetails`, eine Methode `loadUserDetails(token)`, passt als Lambda):
  - `token.getName()` ist der Name, `(String) token.getCredentials()` der Inhalt von `Remote-Groups`.
  - Gruppen in Rollen umsetzen und `User.withUsername(name).password("").roles(...).build()` zurückgeben. `roles("ADMIN")` ergibt die Authority `ROLE_ADMIN`.
  - Fehlt `Remote-Groups`, ruft der Provider den Service gar nicht erst auf, die Anfrage bleibt nicht angemeldet.
- `ProviderManager` liegt in `org.springframework.security.authentication`.

**Filterkette** (Lambda-DSL auf `http`):

- `addFilterAt(filter, AbstractPreAuthenticatedProcessingFilter.class)`
- `sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`
- `csrf(csrf -> csrf.spa())`: Das schreibt das Token bei jeder Antwort in das Cookie `XSRF-TOKEN` (lesbar für JavaScript) und erwartet es im Header `X-XSRF-TOKEN`.
- `exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`: Ohne diese Zeile liefert Spring bei fehlender Anmeldung `403` statt `401`. `HttpStatusEntryPoint` liegt in `org.springframework.security.web.authentication`.
- `authorizeHttpRequests(auth -> auth ...)` mit den Regeln aus Feature 3, in derselben Reihenfolge: `requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus").permitAll()`, `requestMatchers(HttpMethod.POST, "/services/**", "/load").hasRole("ADMIN")`, `requestMatchers(HttpMethod.GET, "/**").hasAnyRole("VIEWER", "ADMIN")`, `anyRequest().denyAll()`.
- `formLogin`, `httpBasic` und `logout` nicht aufrufen. Sobald es eine eigene `SecurityFilterChain` gibt, richtet Spring Boot sie nicht mehr selbst ein.
- Am Ende `return http.build();`.

### `MeController` und `CurrentUser`

- `MeController` im Package `controller` mit `@GetMapping("/me")`. Den Benutzer bekommt die Methode als Parameter `Authentication authentication`, Spring setzt ihn ein. Name über `authentication.getName()`, `admin` ist `true`, wenn `authentication.getAuthorities()` eine Authority `ROLE_ADMIN` enthält.
- Record `CurrentUser(String name, boolean admin)` in `dto`.

### `app.js`

- Alle Aufrufe laufen schon über `request(method, path, body)`. Dort bei jedem `POST` den Header `X-XSRF-TOKEN` setzen, Wert aus dem Cookie `XSRF-TOKEN` (aus `document.cookie` lesen). Das Cookie setzt Spring schon beim ersten `GET` der Seite.
- Beim Start einmal `GET /me`, Name und Rolle anzeigen, bei `admin: false` die Steuerelemente deaktivieren.
- `request()` schickt `Accept: application/json`. Sonst sendet `fetch` `*/*`, und Authelia antwortet einer abgemeldeten Anfrage mit einer Weiterleitung zur Anmeldeseite statt mit `401` (`handler_authz_util.go`, v4.39.28: `if ctx.IsXHR() || !ctx.AcceptsMIME("text/html") { return fasthttp.StatusUnauthorized }`). Bei `401` lädt die Seite neu, Authelia leitet dann zur Anmeldung um. So landet man nach einem Logout in einem anderen Tab oder nach Ablauf der Sitzung spätestens nach 2 Sekunden (Abfrageintervall) bei der Anmeldung. Den Fehler, den Grafana im iframe in der Zeit zeigt, kann die Seite nicht ausblenden.

### Abmelden

- Konfiguration `chaos-controller.logout-url` (Umgebung `CHAOSCONTROLLER_LOGOUTURL`, in `docker-compose.yml` aus `LOGOUT_URL`), die Logout-Seite von Authelia. `GET /config` liefert sie als `logoutUrl` mit. Ohne Wert zeigt die Seite keinen Knopf.
- Unten in der Sidebar über dem Design-Umschalter: Benutzersymbol, Name und ein Pfeil nach rechts, die Rolle als Tooltip. Beim Überfahren mit der Maus (oder Fokus per Tastatur) erscheint rechts daneben ein roter Knopf "Abmelden". Bei eingeklappter Sidebar nur das Symbol, auf schmalen Bildschirmen steht der Knopf fest neben dem Namen (kein Platz rechts, kein Hover auf Touch). Der Knopf führt zu `<logout-url>?rd=<Adresse des Dashboards>`. Die Logout-Seite beendet die Sitzung und schickt den Browser nach etwa 2 Sekunden zu `rd` zurück, von dort geht es zur Anmeldung und danach wieder zum Dashboard. Authelia folgt `rd` nur bei `https` und einem Host unter der Cookie-Domain (`IsSafeRedirectionTargetURI` in `internal/middlewares/authelia_context.go`, v4.39.28), sonst bleibt es auf seiner Startseite.

### Sicherheitsannahme

Der Controller vertraut den Headern. Das ist nur richtig, solange Anfragen von außen ausschließlich über Traefik kommen und Traefik eingehende `Remote-*`-Header durch die von Authelia ersetzt. Auf dem Pi veröffentlicht der Controller deshalb keinen Port nach außen (höchstens `127.0.0.1`). Ob Traefik gefälschte Header wirklich ersetzt, prüft die lokale Testumgebung (`docs/local-auth.md`).

## Warum CSRF

Der Browser schickt das Anmelde-Cookie von Authelia bei jeder Anfrage an `chaosplayground.cooperr.de` von selbst mit, egal welche Seite die Anfrage auslöst. Ohne Schutz könnte eine andere Seite, die der Prof gerade offen hat, unbemerkt einen `POST` an `/services/payment-service/stop` schicken, und Traefik würde ihn als angemeldeten Admin durchlassen (Cross-Site Request Forgery).

Authelia setzt sein Cookie mit `SameSite=Lax` (https://www.authelia.com/configuration/session/introduction/: "The new default is Lax."). Damit schickt der Browser das Cookie bei `POST`s von fremden Websites nicht mit. Andere Dienste unter `*.cooperr.de` gelten für den Browser aber als dieselbe Site, für sie greift Lax nicht. Das CSRF-Token schließt die Lücke: Nur JavaScript, das auf `chaosplayground.cooperr.de` selbst läuft, kann das Cookie `XSRF-TOKEN` lesen und den Header setzen.

## Tests (Akzeptanzkriterien)

- Die bestehenden `@WebMvcTest`-Klassen laufen weiter. `@WebMvcTest` lädt keine `@Configuration`-Klassen, ohne Weiteres gilt dort die Standard-Security von Boot (alles gesperrt). Deshalb `@Import(SecurityConfig.class)` an jede `@WebMvcTest`-Klasse, und die Anfragen schicken die Header mit (`.header("Remote-User", "prof").header("Remote-Groups", "chaos-admin")`). `POST`s brauchen zusätzlich `.with(csrf())`, statisch importiert aus `org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors`.
- `SecurityConfigTest` (`@WebMvcTest({ServiceController.class, LoadController.class})`, `@Import(SecurityConfig.class)`, Service gemockt):
  - `getServices_noUserHeader_returnsUnauthorized`
  - `getServices_viewer_returnsOk`
  - `getServices_unknownGroupOnly_returnsForbidden`
  - `stop_viewer_returnsForbidden`, der Service wird nicht aufgerufen (`verify(..., never())`)
  - `stop_admin_returnsOk`
  - `stop_adminInSeveralGroups_returnsOk` mit `Remote-Groups: dev, chaos-admin`
  - `stop_adminWithoutCsrfToken_returnsForbidden`
  - `updateLoad_viewer_returnsForbidden`
- `MeControllerTest`: `viewer` liefert `admin` `false`, `admin` liefert `true`, ohne Header `401`.
- `contextLoads` läuft weiter, `verify` beim Root ist grün, der Smoke-Test der CI (Health-Check auf `8090/actuator/health`) bleibt grün.

## Manuell prüfen

Nach `docker compose up --build`, direkt gegen den Controller, ohne Traefik (`127.0.0.1:8090`):

```
curl -i localhost:8090/services
curl -i localhost:8090/services -H 'Remote-User: guest' -H 'Remote-Groups: chaos-viewer'
curl -i localhost:8090/me -H 'Remote-User: prof' -H 'Remote-Groups: chaos-admin'
curl -i -X POST localhost:8090/services/payment-service/stop -H 'Remote-User: guest' -H 'Remote-Groups: chaos-viewer'
curl -i localhost:8090/actuator/health
```

Erwartet: `401`, `200`, `200` mit `"admin": true`, `403`, `200`. Den Admin-`POST` mit CSRF-Token und die Oberfläche prüft man im Browser über die lokale Testumgebung.

Ohne Traefik ist die Oberfläche im Browser ab jetzt gesperrt (`401`).

## Nicht Teil dieser Aufgabe

- Traefik, Authelia und die lokale Testumgebung (`docs/local-auth.md`), das Homelab selbst.
- Prüfung der Absender-IP im Controller. Authelia empfiehlt, dass die App Header nur von der IP des Proxys annimmt ("Trusted Remote Networks", Seite zu Trusted Header SSO). Bewusst weggelassen: Im Docker-Netz hängen nur die eigenen Dienste und Traefik, und die IP von Traefik müsste dafür fest vergeben werden.
- OIDC (verworfen zugunsten von Forward-Auth, weniger Konfiguration und derselbe Weg wie bei Grafana).
