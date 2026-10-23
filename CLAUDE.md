# Chaos Playground

Privates Lernprojekt von Colin. Das Repo ist öffentlich lesbar: keine persönlichen Notizen, Namen Dritter, Daten, Arbeitgeberbezüge, Secrets oder Zugangsdaten hier ablegen. Fortschritt und Journal führt Colin außerhalb des Repos.

Sprache mit Colin: Deutsch. Code, Commit-Messages und Bezeichner: Englisch.

## Worum es geht

Ein kleiner Online-Shop aus mehreren Spring-Boot-Diensten, der gezielt sabotiert wird (Ausfall, Latenz, Fehler), um Resilienz-Muster (Circuit Breaker, Retry, Timeout, Fallback) live zu zeigen. Idee, Architektur und Szenarien: `README.md`. Anforderungen pro Baustein: `docs/`.

## Getroffene Entscheidungen

- Ein Git-Repo (Monorepo) mit Maven-Aggregator-POM und einem Modul pro Dienst. Grund: Dienste gehören zusammen, eine gemeinsame `docker-compose.yml`, ein IntelliJ-Projekt mit einem Git-Root.
- Die Root-`pom.xml` hat `spring-boot-starter-parent` als Parent und ist Parent aller Module. Boot-Version, `java.version` und Versions-Overrides stehen nur im Root, nicht in den Modulen.
- Hosting: öffentliches Repo auf GitHub, zusätzlich ein zweites Remote. Pipelines (GitHub Actions) laufen nur auf GitHub. Der Raspberry Pi holt den Code von GitHub.
- Kein Modul `common`, solange es nichts Geteiltes gibt.
- Lokal mit Docker Compose, keine VMs nötig. Mac ist Apple Silicon (arm64). Die Demo läuft später auf einem Raspberry Pi 5 (Portainer, Docker, ebenfalls arm64).
- GroupId `de.cooperr.chaos`, Java 25, Spring Boot 4.1.1, Konfiguration in YAML, ein Maven Wrapper im Root.
- Packages nach Schicht: Unter `de.cooperr.chaos.<dienst>` liegen `controller`, `service`, `client` (nur im Shop), `dto` (Requests, Responses, Enums), `exception` und `config` (`@ConfigurationProperties`). Die Application-Klasse bleibt im Wurzelpackage, damit der Component-Scan alle Unterpackages findet. Tests liegen im selben Package wie die getestete Klasse, die `*ApplicationTest` im Wurzelpackage. Records in `dto` sind `public`, weil sie von mehreren Packages genutzt werden.
- Ports: shop 8080, inventory 8081, payment 8082, chaos-controller 8090, load-generator 8091.
- `spring.mvc.problemdetails.enabled: true` in jedem Dienst, damit Fehlermeldungen (RFC 9457) im JSON ankommen.
- `totalAmount` ist der Gesamtbetrag einer Bestellung (im Shop und im `payment-service`). Der Shop hat keine Preisliste, der Aufrufer gibt den Betrag mit.
- Der Shop wird zuerst ohne Resilienz gebaut, Resilience4j kommt mit Szenario 1. `ServiceClient.call` ist dafür die zentrale Stelle. Geplant (noch nicht entschieden): Für Szenario 1 laufen zwei Instanzen desselben Shops nebeneinander, eine ohne und eine mit Schutz, der Lastgenerator schickt dieselbe Last an beide.
- Betrieb (geplant): Die Demo läuft unter `chaosplayground.cooperr.de` auf dem Pi, davor der Nginx Proxy Manager (TLS dort). Öffentlich erreichbar ist nur der `chaos-controller`, die anderen Dienste bleiben im Docker-Netz.
- Zugang zum Dashboard (geplant): Spring Security mit zwei festen Logins, Formular und Session. `viewer` sieht das Dashboard, `admin` darf zusätzlich die Chaos-Knöpfe bedienen (serverseitig geprüft). Passwörter als BCrypt-Hash per Umgebungsvariable, nicht im Repo. Docker-Zugriff des Controllers nur über einen Socket-Proxy mit Start, Stop und Pause der Projekt-Container, ob ein arm64-Image existiert, ist noch zu prüfen.

## Arbeitsweise

- Colin implementiert selbst. Claude gibt pro Klasse oder Modul die Anforderungen vor (Features, Schnittstellen, Akzeptanzkriterien) und schreibt sie nach `docs/`, Colin setzt um, Claude prüft. Claude schreibt Code erst auf Wunsch.
- Commits direkt auf `main`, keine Issues, Branches oder MRs (Einzelprojekt). Das gilt auch, wenn ein allgemeiner Commit-Skill andere Regeln (Ticket-Keys, Branches) vorgibt.
- Commit-Messages: Englisch, Imperativ im Präsens, ohne Ticket-Key, z. B. "Add circuit breaker for payment calls".
- Commit-Message als eigene Antwort zeigen und auf Colins "ok" warten (keine AskUserQuestion).
- Claude kann in seiner Sandbox nicht pushen (keine Zugangsdaten für die Remotes), das macht Colin mit `git push`. Maven geht offline mit `JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-25.0.5/Contents/Home mvn -o verify` (nicht über `./mvnw`, das will `~/.m2/wrapper` anlegen). Claude behauptet nie, ein Build sei grün, ohne ihn gelaufen zu lassen oder Colins Ausgabe gesehen zu haben, und sagt dazu, was geprüft wurde. Ob der Zugriff auf JDK und `~/.m2` so bleibt, hängt von der Sandbox-Konfiguration ab, im Zweifel Colin fragen.
- Tests im Stil der bereits vorhandenen Tests im Projekt schreiben. Klassennamen enden auf `Test` (nicht `Tests`), Klassen und Methoden sind paketprivat (ohne `public`), Methodennamen folgen `methode_zustand_ergebnis` (z. B. `reserve_insufficientStock_returnsConflict`). Ausnahme: `contextLoads`. Strings werden mit `.formatted(...)` zusammengebaut, nicht mit `+` (z. B. `"%s/payments".formatted(BASE_URL)`).
- Spring Boot 4: `@WebMvcTest` liegt in `org.springframework.boot.webmvc.test.autoconfigure`, `@MockBean` gibt es nicht mehr, stattdessen `@MockitoBean` aus `org.springframework.test.context.bean.override.mockito`. Bei Boot-Fragen im Quelltext oder in den Jars unter `~/.m2` nachsehen statt aus dem Gedächtnis zu antworten.
- IntelliJ-Run-Konfigurationen brauchen die VM-Option `-Djava.rmi.server.hostname=localhost`, sonst spammt der JMX-Agent `LocalRMIServerSocketFactory`-Fehler in die Konsole. Die Konfigurationen liegen in `.run/`.
- Keine Secrets, Zugangsdaten oder fremden internen Inhalte in dieses Projekt.
