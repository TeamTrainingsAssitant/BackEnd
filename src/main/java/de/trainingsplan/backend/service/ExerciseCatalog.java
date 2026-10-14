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

    private static final int MAX_ATTEMPTS = 3;

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
        new Thread(this::load, "exercise-catalog-loader").start();
    }

    /**
     * Lädt alle Seiten. Die bisherige Liste wird erst ersetzt, wenn alles geladen ist.
     * Schlägt das Laden fehl, bleibt die alte Liste erhalten.
     */
    public void load() {
        try {
            List<ExternalExercise> loaded = new ArrayList<>();
            String cursor = null;
            do {
                ExercisePage page = loadPageWithRetry(cursor);
                loaded.addAll(page.exercises());
                cursor = page.nextCursor();
                pause(pauseMillis);
            } while (cursor != null);

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

    /** Holt eine Seite, bei einem Fehler (z. B. Rate-Limit) wird nach einer Pause neu versucht. */
    private ExercisePage loadPageWithRetry(String cursor) {
        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return client.listExercises(cursor);
            } catch (ApiException e) {
                log.warn("ExerciseDB page failed (attempt {}), waiting before retry", attempt);
                pause(retryMillis);
            }
        }
        return client.listExercises(cursor);
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

    public List<String> getMuscles() {
        return muscles;
    }

    public List<String> getBodyParts() {
        return bodyParts;
    }
}
