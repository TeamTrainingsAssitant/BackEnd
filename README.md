# BackEnd

Spring-Boot-Backend (Java 25) des TrainingsPlanAssistant. Es verwaltet Benutzer, Trainingspläne und die Übungen darin und ist als einzige Komponente mit der Datenbank und ExerciseDB verbunden. Alle Endpunkte liegen unter `/api/v1`.

```
Frontend ──▶ REST-API (dieses Repo) ──┬──▶ PostgreSQL
GraphQL-Gateway ──▶                   └──▶ ExerciseDB
```

## Stack

Java 25, Spring Boot 4, Spring Security, Spring Data JPA, PostgreSQL, Maven, JUnit, Mockito, AssertJ. Die CI (`.github/workflows/ci.yml`) führt bei jedem Push auf `main` `./mvnw -B verify` aus.

## Starten und testen

macOS/Linux:

```
./mvnw spring-boot:run     # braucht PostgreSQL auf localhost:5432 (DB "trainingsplan")
./mvnw test                # reine Unit-Tests, keine Datenbank nötig
```

Windows:

```
mvnw.cmd spring-boot:run
mvnw.cmd test
```

Es gibt bewusst nur Unit-Tests (Mockito, ohne Spring-Kontext und ohne Datenbank).
Das Zusammenspiel mit der Datenbank wird gegen die deployte Heroku-Version getestet.

### Aufbau der Tests

Die Tests liegen unter `src/test/java` im selben Package wie die getestete Klasse und heißen `<Klasse>Test`.

| Package | Was geprüft wird |
|---|---|
| `controller` (+ `dto`) | Antwort-Status und -Meldungen, Weitergabe an den Service, DTO-Mapping, Validierungsregeln |
| `service` | Fachlogik: Registrierung, Login, Token (Hash, Ablauf, Aufräumen), Pläne inkl. Besitzprüfung, Übungskatalog (Laden, Suche, Filter) |
| `security` | `TokenAuthenticationFilter`: Bearer-Header, Randfälle, Kette läuft immer weiter |
| `exception` | `GlobalExceptionHandler`: Status und einheitliches Fehlerformat |
| `config` | `PasswordEncoder` (BCrypt) |
| `model` | Logik in Entities (z. B. `Plan.update`) |

So wird getestet:

- Abhängigkeiten (Repositories, andere Services) werden mit `mock(...)` ersetzt, die Klasse selbst per Konstruktor gebaut.
- Assertions mit AssertJ, aufgezeichnete Argumente mit `ArgumentCaptor`.
- Entities haben keinen ID-Setter; die ID wird im Test mit `ReflectionTestUtils.setField(obj, "id", 1L)` gesetzt.
- Zeit wird nicht eingefroren, sondern mit Zeitfenstern geprüft (`isBetween(vorher, nachher)`).
- Bewusst nicht per Unit-Test geprüft: die `SecurityFilterChain` (öffentliche Routen, 401-Antwort), weil dafür ein Spring-Kontext nötig wäre.

`JAVA_HOME` muss auf ein JDK 25 zeigen. Die DB-Verbindung lässt sich über `JDBC_DATABASE_URL`,
`JDBC_DATABASE_USERNAME` und `JDBC_DATABASE_PASSWORD` überschreiben (auf Heroku setzt der Buildpack sie selbst).

## Authentifizierung

| Methode | URI | Zugriff | Beschreibung |
|---|---|---|---|
| POST | `/users` | öffentlich | Registrieren. Das Passwort wird mit BCrypt gehasht gespeichert. |
| POST | `/auth` | öffentlich | Login mit `email` und `password`. Das Token kommt im Cookie `capsule_session` (`HttpOnly`), nicht im Body. |
| DELETE | `/auth` | öffentlich | Logout. Das Token steht im Body oder im Cookie und wird serverseitig gelöscht. |
| GET | `/users/me` | Token nötig | Profil des angemeldeten Benutzers. |
| GET | `/users/me/export` | Token nötig | Alle persönlichen Daten als `{"user":{...},"plans":[...]}`, Pläne mit Übungen in Reihenfolge. |
| DELETE | `/users/me` | Token nötig | Konto löschen. Das Passwort steht im Body (`{"password":"..."}`). Löscht auch alle Pläne und Tokens. Bei falschem Passwort `400`. |
| GET | `/exercises?name=push&limit=20` | Token nötig | Durchsucht ExerciseDB über das Backend und liefert Übungsname, GIF sowie Muskelgruppen. |
| GET | `/plans` | Token nötig | Liste der eigenen Pläne. |
| POST | `/plans` | Token nötig | Legt einen Plan an (`name` Pflicht). |
| GET, PUT, DELETE | `/plans/{planId}` | Token nötig | Plan lesen, ändern oder samt Übungen löschen. |
| GET | `/plans/{planId}/exercises` | Token nötig | Liefert die Übungen eines eigenen Plans in Positionsreihenfolge. |
| POST | `/plans/{planId}/exercises` | Token nötig | Fügt eine ExerciseDB-Übung hinzu (`externalId`, optional `note`, `weight` in kg von 0 bis 1000, `sets` von 1 bis 20, `reps` von 1 bis 100). Werte außerhalb der Grenzen: `400`. `weight` und `reps` nur zusammen angeben, sonst `400`. Ohne Werte hat die Übung noch keine Sätze. |
| PUT | `/plans/{planId}/exercises/{planExerciseId}` | Token nötig | Aktualisiert `note` und optional `trainingSets` einer Planübung. Fehlt `trainingSets`, bleiben vorhandene Sätze erhalten. Die Liste enthält 1 bis 20 Sätze; `weight` und `reps` sind pro Satz erforderlich. |
| GET | `/plans/{planId}/exercises/{planExerciseId}/alternatives?limit=5` | Token nötig | Empfiehlt bis zu 20 noch nicht im Plan enthaltene Übungen mit möglichst ähnlichen Muskelgruppen. Standard: 5 Vorschläge. |
| PUT | `/plans/{planId}/exercises/{planExerciseId}/replacement` | Token nötig | Ersetzt die Übung mit `{"externalId":"..."}` und behält Position, Notiz sowie Sätze, Wiederholungen und Gewicht. |
| PUT | `/plans/{planId}/exercises/order` | Token nötig | Ändert die Reihenfolge der Übungen. Der Body enthält alle `planExerciseId`s des Plans in der neuen Reihenfolge (`{"exerciseIds":[3,1,2]}`), die Positionen werden ab 1 neu vergeben. Antwort: die Übungen in neuer Reihenfolge. Doppelte, fehlende oder fremde IDs: `400`, fremder oder unbekannter Plan: `404`. |
| DELETE | `/plans/{planId}/exercises/{planExerciseId}` | Token nötig | Entfernt eine Übung aus einem Plan. |

**Alle anderen Endpunkte verlangen den Header `Authorization: Bearer <token>` oder das Cookie `capsule_session`.**
Ohne gültiges Token antwortet das Backend mit `401 {"message":"Not authenticated."}`.

### Ablauf

1. Registrieren: `POST /users` mit `{"username","email","password"}`. Das Passwort braucht mindestens 8 Zeichen.
2. Einloggen: `POST /auth` mit `{"email","password"}` liefert `{"message":"User logged on.","token":null}` und setzt das Cookie `capsule_session`.
3. Cookie bei jedem weiteren Aufruf mitsenden oder seinen Wert im Header `Authorization: Bearer <token>` übergeben.
4. Ausloggen: `DELETE /auth` mit `{"token":"..."}` oder mit dem Cookie. Danach ist das Token ungültig.

```
curl -X POST http://localhost:8080/api/v1/users -H "Content-Type: application/json" \
  -d '{"username":"max","email":"max@mosbach.de","password":"super-safe"}'

# -i zeigt den Header Set-Cookie, dort steht der Wert für <token>
curl -i -X POST http://localhost:8080/api/v1/auth -H "Content-Type: application/json" \
  -d '{"email":"max@mosbach.de","password":"super-safe"}'

curl http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <token>"

curl http://localhost:8080/api/v1/users/me/export -H "Authorization: Bearer <token>"

curl -X DELETE http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"password":"super-safe"}'

curl -X DELETE http://localhost:8080/api/v1/auth -H "Content-Type: application/json" \
  -d '{"token":"<token>"}'
```

### Details

- **Tokens** sind zufällige 32-Byte-Werte (kein JWT). In der Datenbank steht nur der SHA-256-Hash, nie das Token selbst.
- **Gültigkeit:** 24 Stunden, einstellbar mit `app.auth.token-ttl` (z. B. `30m`, `12h`).
  Abgelaufene Tokens werden abgelehnt und beim nächsten Login aufgeräumt.
- **E-Mail** wird getrimmt und kleingeschrieben gespeichert, `Max@Mosbach.de` und `max@mosbach.de` sind derselbe Account.
- **Login-Fehler:** Unbekannte E-Mail und falsches Passwort liefern beide `400 "Password wrong."`,
  damit man keine registrierten E-Mails erraten kann.

### Fehlerformat

Jeder Fehler hat die Form `{ "message": "..." }`:

| Status | Beispiel |
|---|---|
| 400 | `Email or password invalid.`, `Password wrong.`, `Token wrong.`, `Invalid request.` |
| 401 | `Not authenticated.` |
| 404 | `Not found.` (unbekannte URL, nur mit gültigem Token; ohne Token kommt 401) |
| 409 | `Email already registered.` |
| 500 | `Internal server error.` (Details nur im Server-Log) |
| 502 | `ExerciseDB not reachable.` (ExerciseDB nicht erreichbar oder Übungsliste noch nicht geladen) |

### Neue geschützte Endpunkte

Es muss nichts extra konfiguriert werden: Jeder neue Endpunkt ist automatisch geschützt.
Den angemeldeten Benutzer holt man im Controller mit `@AuthenticationPrincipal User user`.
Fehler wirft man mit `throw new ApiException(HttpStatus.NOT_FOUND, "Plan not found.")`.
Wenn ein Endpunkt öffentlich sein soll, muss er in `SecurityConfig` ergänzt werden.

## ExerciseDB

Die Übungen kommen von der ExerciseDB V1 (Free), `https://oss.exercisedb.dev/api/v1`.
Nur nicht-kommerzielle Nutzung, im Frontend ist der Hinweis "Daten: ExerciseDB" Pflicht.
Das Frontend spricht nur mit dem Backend, nie direkt mit ExerciseDB.

| Methode | URI | Zugriff | Beschreibung |
|---|---|---|---|
| GET | `/exercisedb/exercises` | Token nötig | Übungsliste. Query: `name`, `muscles`, `offset` (Standard 0), `limit` (Standard 20, maximal 50). Antwort `{"total":12,"exercises":[...]}`. |
| GET | `/exercisedb/exercises/{externalId}` | Token nötig | Details einer Übung inkl. `instructions`. Unbekannte ID: `404 "Exercise not found."`. |
| GET | `/exercisedb/muscles` | Token nötig | Alle Muskelnamen als Liste, für die Filter-Auswahl. |
| GET | `/exercisedb/bodyparts` | Token nötig | Alle Körperbereiche (z. B. `chest`, `back`) als Liste. |

```
curl "http://localhost:8080/api/v1/exercisedb/exercises?name=bench&muscles=pectorals,triceps&limit=5" \
  -H "Authorization: Bearer <token>"

curl http://localhost:8080/api/v1/exercisedb/exercises/EIeI8Vf -H "Authorization: Bearer <token>"
```

### Filter

- `name`: Teil des Namens, Groß- und Kleinschreibung egal.
- `muscles`: kommagetrennt, z. B. `pectorals,triceps`. Eine Übung passt, wenn **alle**
  genannten Muskeln primär oder sekundär vorkommen.
- `name` und `muscles` gelten zusammen: beides muss passen.
- `total` ist die Trefferzahl ohne Paging. Ein `offset` hinter dem Ende liefert eine leere Liste.

### Wie die Daten geladen werden

Die kostenlose ExerciseDB hat ein Rate-Limit (bei Tests kam nach etwa 20 Anfragen pro Minute ein `429`)
und liefert höchstens 25 Übungen pro Seite. `offset` und ein Muskelfilter mit mehreren Muskeln
funktionieren dort nicht. Deshalb ruft das Backend nicht bei jeder Anfrage ExerciseDB auf:

1. Beim Start lädt der `ExerciseCatalog` in einem Hintergrund-Thread alle Übungen
   (ca. 1500, Seite für Seite per `after=<nextCursor>`) und hält sie **im Speicher**.
   Das dauert etwa 2 Minuten, die Anwendung ist währenddessen schon erreichbar.
2. Suche, Filter und Paging macht das Backend selbst.
3. Klappt eine Seite nicht (z. B. `429`), wird sie nach einer Pause wiederholt. Schlägt auch das
   fehl, startet das Laden später von vorn, bis es klappt. Eine schon geladene Liste wird erst ersetzt,
   wenn die neue komplett ist.
4. Solange der Katalog leer ist, antworten alle `/exercisedb`-Endpunkte mit `502 "ExerciseDB not reachable."`.
5. Beim Hinzufügen einer Übung zu einem Plan wird zuerst im Katalog nachgeschlagen und nur sonst ExerciseDB gefragt.

Was in der Datenbank liegt: nur Benutzer, Pläne und die Übungen in Plänen (mit `externalId`, Name,
`gifUrl` und Muskelgruppen). Der Katalog liegt nur im Speicher und ist nach einem Neustart weg.

Feldmapping: `exerciseId` wird zu `externalId`, `targetMuscles` zu `primaryMuscleGroups`,
`secondaryMuscles` zu `secondaryMuscleGroups`.

Einstellbar in `application.properties` (die Werte unten sind die Standardwerte):

```
app.exercisedb.base-url=https://oss.exercisedb.dev/api/v1
# Pause zwischen zwei Seiten in Millisekunden
app.exercisedb.page-pause-ms=2000
# Wartezeit nach einem Fehler in Millisekunden
app.exercisedb.retry-wait-ms=60000
```
