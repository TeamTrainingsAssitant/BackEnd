package de.trainingsplan.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.trainingsplan.backend.exception.ApiException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Einziger Zugriff auf ExerciseDB. Hier wird auch das Feldmapping gemacht. */
@Service
public class ExerciseDbClient {

    /** Mehr als 25 Übungen pro Seite liefert ExerciseDB nicht. */
    static final int PAGE_SIZE = 25;

    private final RestClient restClient;

    public ExerciseDbClient(@Value("${app.exercisedb.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    /** Holt eine Übung. Unbekannte ID: 404, ExerciseDB nicht erreichbar: 502. */
    public ExternalExercise getExercise(String externalId) {
        try {
            ExerciseResponse response = restClient.get()
                    .uri("/exercises/{id}", externalId)
                    .retrieve()
                    .body(ExerciseResponse.class);
            if (response == null || response.data() == null) {
                throw notReachable();
            }
            return response.data().toExternalExercise();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Exercise not found.");
            }
            throw notReachable();
        } catch (RestClientException e) {
            throw notReachable();
        }
    }

    /** Sucht Übungen für die Trainingsbibliothek des Frontends. */
    public List<ExternalExercise> searchExercises(String name, int limit) {
        try {
            ListResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/exercises").queryParam("limit", limit);
                        if (StringUtils.hasText(name)) {
                            uriBuilder.queryParam("name", name.trim());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(ListResponse.class);
            if (response == null || response.data() == null) {
                throw notReachable();
            }
            return response.data().stream().map(Data::toExternalExercise).toList();
        } catch (RestClientException e) {
            throw notReachable();
        }
    }

    /**
     * Holt eine Seite der Übungsliste (25 Stück). Die erste Seite wird mit {@code afterCursor = null}
     * geholt, die nächste mit dem {@code nextCursor} der vorherigen Seite.
     */
    public ExercisePage listExercises(String afterCursor) {
        try {
            String path = "/exercises?limit=" + PAGE_SIZE;
            if (StringUtils.hasText(afterCursor)) {
                path += "&after=" + afterCursor;
            }
            ListResponse response = restClient.get().uri(path).retrieve().body(ListResponse.class);
            if (response == null || response.data() == null) {
                throw notReachable();
            }
            List<ExternalExercise> exercises = response.data().stream().map(Data::toExternalExercise).toList();
            boolean hasNext = response.meta() != null && response.meta().hasNextPage();
            String nextCursor = hasNext ? response.meta().nextCursor() : null;
            return new ExercisePage(exercises, nextCursor);
        } catch (RestClientException e) {
            throw notReachable();
        }
    }

    /** Alle Muskelnamen, z. B. für die Filter-Auswahl. */
    public List<String> getMuscles() {
        return getNames("/muscles");
    }

    /** Alle Körperbereiche, z. B. chest oder back. */
    public List<String> getBodyParts() {
        return getNames("/bodyparts");
    }

    private List<String> getNames(String path) {
        try {
            NamesResponse response = restClient.get().uri(path).retrieve().body(NamesResponse.class);
            if (response == null || response.data() == null) {
                throw notReachable();
            }
            return response.data().stream().map(Name::name).toList();
        } catch (RestClientException e) {
            throw notReachable();
        }
    }

    private static ApiException notReachable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "ExerciseDB not reachable.");
    }

    /** Übung in unseren Feldnamen (targetMuscles wird zu primaryMuscleGroups usw.). */
    public record ExternalExercise(String externalId, String name, String gifUrl,
            List<String> primaryMuscleGroups, List<String> secondaryMuscleGroups,
            List<String> bodyParts, List<String> equipments, List<String> instructions) {
    }

    /** Eine Seite der Übungsliste. {@code nextCursor} ist null auf der letzten Seite. */
    public record ExercisePage(List<ExternalExercise> exercises, String nextCursor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExerciseResponse(Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ListResponse(List<Data> data, Meta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Meta(boolean hasNextPage, String nextCursor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NamesResponse(List<Name> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Name(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Data(String exerciseId, String name, String gifUrl,
            List<String> targetMuscles, List<String> secondaryMuscles,
            List<String> bodyParts, List<String> equipments, List<String> instructions) {

        ExternalExercise toExternalExercise() {
            return new ExternalExercise(exerciseId, name, gifUrl,
                    orEmpty(targetMuscles), orEmpty(secondaryMuscles),
                    orEmpty(bodyParts), orEmpty(equipments), orEmpty(instructions));
        }

        private static List<String> orEmpty(List<String> list) {
            return list == null ? List.of() : list;
        }
    }
}
