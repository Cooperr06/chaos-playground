# Aufgabe: CI-Pipeline auf GitHub

Eine GitHub-Actions-Pipeline baut und testet das Projekt bei jedem Push und Pull Request auf `main`. Sobald es die Dockerfiles gibt (siehe `docs/docker-compose.md`), baut sie zusätzlich die Images der Dienste und legt sie in der GitHub Container Registry (`ghcr.io`) ab. Kein Deployment (CD), der Pi holt sich den Stand später selbst.

## Warum Container Registry und nicht Workflow-Artifacts

Workflow-Artifacts sind Dateien, die an einem einzelnen Lauf hängen und nach einer Aufbewahrungsfrist gelöscht werden. Ein Image müsste man dort als Tar-Datei ablegen und von Hand mit `docker load` laden. Die Container Registry ist für Images gedacht: `docker pull ghcr.io/<owner>/<image>:<tag>` funktioniert direkt, auch auf dem Pi und in Portainer. Die Images erscheinen auf GitHub unter "Packages" des Repos.

## Stufe 1: Build und Tests

1. Datei `.github/workflows/ci.yml`.
2. Trigger: `push` auf `main` und `pull_request` auf `main`.
3. Ein Job auf einem von GitHub gehosteten Linux-Runner:
   - Code auschecken (`actions/checkout`).
   - JDK 25 einrichten (`actions/setup-java`), Distribution Temurin wie lokal und in den Images, Maven-Cache über die eingebaute `cache`-Option der Action.
   - `./mvnw -B verify` im Root (Batch-Modus, alle Module mit Tests). Der Wrapper ist im Git schon ausführbar markiert.
4. Rechte des `GITHUB_TOKEN` auf das Nötigste beschränken (`permissions: contents: read`).
5. Versionen der Actions auf deren GitHub-Seite nachsehen (aktuelles Major-Tag), nicht aus dem Gedächtnis nehmen.

## Stufe 2: Docker-Images (nach der Docker-Compose-Aufgabe)

1. Ein zweiter Job, der nach dem Build-Job läuft (`needs`), nur bei Push auf `main`, nicht bei Pull Requests.
2. Pro Dienst ein Image (`shop-service`, `inventory-service`, `payment-service`), gebaut mit Kontext `.` und `<modul>/Dockerfile`, wie in der Compose-Datei. Eine Matrix über die Module hält das kurz.
3. Plattformen `linux/arm64` (Mac, Pi) und gern `linux/amd64`. Zwei Wege, bitte in der Doku nachsehen und einen wählen:
   - Docker Buildx mit QEMU-Emulation auf dem normalen Runner (langsamer, ein Job).
   - Ein arm64-Runner von GitHub, falls für öffentliche Repos verfügbar.
4. Anmelden an `ghcr.io` mit dem `GITHUB_TOKEN`, dafür braucht der Job `packages: write`. Kein persönliches Token.
5. Image-Namen `ghcr.io/cooperr06/<modul>` (Registry-Namen sind kleingeschrieben), Tags `latest` und der Commit-SHA.
6. Sichtbarkeit der Pakete prüfen: Neue Pakete sind auf GitHub oft zuerst privat. Für den Pi entweder öffentlich stellen oder dort anmelden.

## Vorgaben

- Keine Secrets im Workflow. Das `GITHUB_TOKEN` stellt GitHub automatisch bereit.
- Die Pipeline nutzt dieselben Befehle wie lokal (`mvnw verify`, dieselben Dockerfiles), keine Sonderlogik nur für CI.
- Kein Badge, kein Release-Prozess, keine Versionsnummern im ersten Schritt.

## Akzeptanzkriterien

- Stufe 1: Ein Push auf `main` startet den Workflow, er ist grün, unter "Actions" sind die Testergebnisse im Log sichtbar. Ein absichtlich kaputter Test (lokal, nicht committen oder in einem PR) macht ihn rot.
- Stufe 2: Nach einem Push auf `main` liegen die drei Images unter "Packages", `docker pull ghcr.io/cooperr06/payment-service:latest` funktioniert auf dem Mac (arm64), und der Container startet.

## Nicht Teil dieser Aufgabe

- Deployment auf den Pi (CD)
- Pipelines auf dem zweiten Remote
- Code-Analyse, Abhängigkeits-Updates (z. B. Dependabot), Releases
