package de.trainingsplan.backend.service;

import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.service.ExerciseDbClient.ExercisePage;
import de.trainingsplan.backend.service.ExerciseDbClient.ExternalExercise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Hält alle ExerciseDB-Übungen im Speicher (nicht in der DB). Die kostenlose ExerciseDB hat ein
 * Rate-Limit und liefert nur 25 Übungen pro Seite. Deshalb wird die Liste einmal beim Start im
 * Hintergrund geladen, langsam und mit Wiederholung.
 */
@Service
public class ExerciseCatalog {

    private static final Logger log = LoggerFactory.getLogger(ExerciseCatalog.class);

    /** So oft wird eine Seite versucht, bevor das Laden abgebrochen wird. */
    private static final int MAX_ATTEMPTS = 3;

    /** Schutz vor einer Endlosschleife: ExerciseDB hat etwa 60 Seiten (1500 Übungen / 25). */
    static final int MAX_PAGES = 200;

    private final ExerciseDbClient client;
    private final long pauseMillis;
    private final long retryMillis;

    // volatile: Der Lade-Thread setzt die Listen, die Web-Threads lesen sie.
    private volatile List<ExternalExercise> exercises = List.of();
    private volatile Map<String, ExternalExercise> exercisesById = Map.of();
    private volatile List<String> muscles = List.of();
    private volatile List<String> bodyParts = List.of();

    public ExerciseCatalog(ExerciseDbClient client,
            @Value("${app.exercisedb.page-pause-ms:2000}") long pauseMillis,
            @Value("${app.exercisedb.retry-wait-ms:60000}") long retryMillis) {
        this.client = client;
        this.pauseMillis = pauseMillis;
        this.retryMillis = retryMillis;
    }

    /** Startet das Laden nach dem Start der Anwendung in einem eigenen Thread. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadInBackground() {
        new Thread(this::loadUntilLoaded, "exercise-catalog-loader").start();
    }

    /** Versucht das Laden so lange, bis es klappt (z. B. wenn das Rate-Limit gerade greift). */
    void loadUntilLoaded() {
        while (!isLoaded() && !Thread.currentThread().isInterrupted()) {
            load();
            if (!isLoaded()) {
                pause(retryMillis);
            }
        }
    }

    /**
     * Lädt alle Seiten. Die bisherige Liste wird erst ersetzt, wenn alles geladen ist.
     * Schlägt das Laden fehl, bleibt die alte Liste erhalten.
     */
    public void load() {
        try {
            List<ExternalExercise> loaded = new ArrayList<>();
            String cursor = null;
            int pages = 0;
            do {
                ExercisePage page = loadPageWithRetry(cursor);
                loaded.addAll(page.exercises());
                cursor = page.nextCursor();
                pages++;
                pause(pauseMillis);
            } while (cursor != null && pages < MAX_PAGES);

            List<String> loadedMuscles = client.getMuscles();
            List<String> loadedBodyParts = client.getBodyParts();

            Map<String, ExternalExercise> byId = new HashMap<>();
            for (ExternalExercise exercise : loaded) {
                byId.put(exercise.externalId(), exercise);
            }
            exercises = loaded;
            exercisesById = byId;
            muscles = loadedMuscles;
            bodyParts = loadedBodyParts;
            log.info("ExerciseDB catalog loaded: {} exercises", loaded.size());
        } catch (ApiException e) {
            log.warn("ExerciseDB catalog could not be loaded: {}", e.getMessage());
        }
    }

    /** Holt eine Seite. Bei einem Fehler (z. B. Rate-Limit) wird nach einer Pause neu versucht. */
    private ExercisePage loadPageWithRetry(String cursor) {
        int attempt = 1;
        while (true) {
            try {
                return client.listExercises(cursor);
            } catch (ApiException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("ExerciseDB page failed (attempt {} of {}), waiting before retry", attempt, MAX_ATTEMPTS);
                attempt++;
                pause(retryMillis);
            }
        }
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** True, sobald die Liste einmal vollständig geladen wurde. */
    public boolean isLoaded() {
        return !exercises.isEmpty();
    }

    public List<ExternalExercise> getAll() {
        return exercises;
    }

    /** Gibt die Übung zurück oder null, wenn sie nicht im Katalog ist. */
    public ExternalExercise findById(String externalId) {
        return exercisesById.get(externalId);
    }

    /**
     * Sucht im Katalog. {@code name} ist ein Teil des Namens (null oder leer: kein Filter),
     * {@code muscles}: die Übung muss mindestens einen davon primär oder sekundär trainieren
     * (null oder leer: kein Filter). Danach wird die Seite ab {@code offset} mit {@code limit}
     * Einträgen ausgeschnitten, {@code total} ist die Trefferzahl vor dem Ausschneiden.
     */
    public SearchResult search(String name, List<String> muscles, int offset, int limit) {
        List<ExternalExercise> hits = new ArrayList<>();
        for (ExternalExercise exercise : exercises) {
            if (matchesName(exercise, name) && matchesMuscles(exercise, muscles)) {
                hits.add(exercise);
            }
        }

        int from = Math.min(Math.max(offset, 0), hits.size());
        int to = Math.min(from + Math.max(limit, 0), hits.size());
        return new SearchResult(hits.size(), hits.subList(from, to));
    }

    private boolean matchesName(ExternalExercise exercise, String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        return exercise.name().toLowerCase().contains(name.trim().toLowerCase());
    }

    private boolean matchesMuscles(ExternalExercise exercise, List<String> wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return true;
        }
for (String muscle : wanted) {
    boolean found = containsIgnoreCase(exercise.primaryMuscleGroups(), muscle)
            || containsIgnoreCase(exercise.secondaryMuscleGroups(), muscle);
    if (!found) {
        return false;
    }
}
return true;
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        for (String entry : list) {
            if (entry.equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    /** Eine Seite Suchergebnisse mit der Gesamtzahl der Treffer. */
    public record SearchResult(int total, List<ExternalExercise> exercises) {
    }

    public List<String> getMuscles() {
        return muscles;
    }

    public List<String> getBodyParts() {
        return bodyParts;
    }
}
