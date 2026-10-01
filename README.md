# BackEnd

Spring-Boot-Backend (Java 25) des TrainingsPlanAssistant. Alle Endpunkte liegen unter `/api/v1`.

## Starten und testen

```
mvnw.cmd spring-boot:run   # braucht PostgreSQL auf localhost:5432 (DB "trainingsplan")
mvnw.cmd test              # nutzt eine In-Memory-H2-Datenbank
```

`JAVA_HOME` muss auf ein JDK 25 zeigen. Die DB-Verbindung lässt sich über `JDBC_DATABASE_URL`,
`JDBC_DATABASE_USERNAME` und `JDBC_DATABASE_PASSWORD` überschreiben (auf Heroku setzt der Buildpack sie selbst).

## Authentifizierung

| Methode | URI | Zugriff | Beschreibung |
|---|---|---|---|
| POST | `/users` | öffentlich | Registrieren. Das Passwort wird mit BCrypt gehasht gespeichert. |
| POST | `/auth` | öffentlich | Login mit `email` und `password`, liefert ein Token. |
| DELETE | `/auth` | öffentlich | Logout. Das Token steht im Body und wird serverseitig gelöscht. |
| GET | `/users/me` | Token nötig | Profil des angemeldeten Benutzers. |

**Alle anderen Endpunkte verlangen den Header `Authorization: Bearer <token>`.**
Ohne gültiges Token antwortet das Backend mit `401 {"message":"Not authenticated."}`.

### Ablauf

1. Registrieren: `POST /users` mit `{"username","email","password"}`. Das Passwort braucht mindestens 8 Zeichen.
2. Einloggen: `POST /auth` mit `{"email","password"}` liefert `{"message":"User logged on.","token":"..."}`.
3. Token bei jedem weiteren Aufruf im Header `Authorization: Bearer <token>` mitsenden.
4. Ausloggen: `DELETE /auth` mit `{"token":"..."}`. Danach ist das Token ungültig.

```
curl -X POST http://localhost:8080/api/v1/users -H "Content-Type: application/json" \
  -d '{"username":"max","email":"max@mosbach.de","password":"super-safe"}'

curl -X POST http://localhost:8080/api/v1/auth -H "Content-Type: application/json" \
  -d '{"email":"max@mosbach.de","password":"super-safe"}'

curl http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <token>"

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

### Neue geschützte Endpunkte

Es muss nichts extra konfiguriert werden: Jeder neue Endpunkt ist automatisch geschützt.
Den angemeldeten Benutzer holt man im Controller mit `@AuthenticationPrincipal User user`.
Fehler wirft man mit `throw new ApiException(HttpStatus.NOT_FOUND, "Plan not found.")`.
Wenn ein Endpunkt öffentlich sein soll, muss er in `SecurityConfig` ergänzt werden.
