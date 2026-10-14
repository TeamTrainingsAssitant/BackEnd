package de.trainingsplan.backend.controller;

import de.trainingsplan.backend.controller.dto.ExerciseDetailResponse;
import de.trainingsplan.backend.controller.dto.ExerciseListItemResponse;
import de.trainingsplan.backend.controller.dto.ExerciseListResponse;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.service.ExerciseCatalog;
import de.trainingsplan.backend.service.ExerciseCatalog.SearchResult;
import de.trainingsplan.backend.service.ExerciseDbClient.ExternalExercise;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Übungen aus der ExerciseDB. Die Daten kommen aus dem {@link ExerciseCatalog} und werden nicht gespeichert. */
@RestController
@RequestMapping("/exercisedb")
public class ExerciseDbController {

    private static final int MAX_LIMIT = 50;

    private final ExerciseCatalog catalog;

    public ExerciseDbController(ExerciseCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/exercises")
    public ExerciseListResponse list(@RequestParam(required = false) String name,
            @RequestParam(required = false) List<String> muscles,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        requireLoaded();
        SearchResult result = catalog.search(name, muscles, offset, Math.min(limit, MAX_LIMIT));
        List<ExerciseListItemResponse> items = result.exercises().stream()
                .map(ExerciseListItemResponse::from)
                .toList();
        return new ExerciseListResponse(result.total(), items);
    }

    @GetMapping("/exercises/{externalId}")
    public ExerciseDetailResponse get(@PathVariable String externalId) {
        requireLoaded();
        ExternalExercise exercise = catalog.findById(externalId);
        if (exercise == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Exercise not found.");
        }
        return ExerciseDetailResponse.from(exercise);
    }

    @GetMapping("/muscles")
    public List<String> muscles() {
        requireLoaded();
        return catalog.getMuscles();
    }

    @GetMapping("/bodyparts")
    public List<String> bodyParts() {
        requireLoaded();
        return catalog.getBodyParts();
    }

    /** Solange der Katalog noch lädt (oder das Laden fehlgeschlagen ist), antworten wir mit 502. */
    private void requireLoaded() {
        if (!catalog.isLoaded()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ExerciseDB not reachable.");
        }
    }
}
