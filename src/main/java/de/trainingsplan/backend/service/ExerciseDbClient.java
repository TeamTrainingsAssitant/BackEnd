package de.trainingsplan.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.trainingsplan.backend.exception.ApiException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Einziger Zugriff auf ExerciseDB. Hier wird auch das Feldmapping gemacht. */
@Service
public class ExerciseDbClient {

    private final RestClient restClient;

    public ExerciseDbClient(@Value("${app.exercisedb.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    /** Holt eine Übung. Unbekannte ID: 404, ExerciseDB nicht erreichbar: 502. */
    public ExternalExercise getExercise(String externalId) {
        try {
            Response response = restClient.get()
                    .uri("/exercises/{id}", externalId)
                    .retrieve()
                    .body(Response.class);
            if (response == null || response.data() == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "ExerciseDB not reachable.");
            }
            return response.data().toExternalExercise();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Exercise not found.");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ExerciseDB not reachable.");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ExerciseDB not reachable.");
        }
    }

    /** Übung in unseren Feldnamen (targetMuscles wird zu primaryMuscleGroups usw.). */
    public record ExternalExercise(String externalId, String name, String gifUrl,
            List<String> primaryMuscleGroups, List<String> secondaryMuscleGroups) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Response(Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Data(String exerciseId, String name, String gifUrl,
            List<String> targetMuscles, List<String> secondaryMuscles) {

        ExternalExercise toExternalExercise() {
            return new ExternalExercise(exerciseId, name, gifUrl,
                    targetMuscles == null ? List.of() : targetMuscles,
                    secondaryMuscles == null ? List.of() : secondaryMuscles);
        }
    }
}
