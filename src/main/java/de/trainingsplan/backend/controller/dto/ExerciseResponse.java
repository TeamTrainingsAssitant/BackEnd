package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.service.ExerciseDbClient.ExternalExercise;
import java.util.List;

public record ExerciseResponse(String externalId, String name, String gifUrl,
        List<String> primaryMuscleGroups, List<String> secondaryMuscleGroups) {

    public static ExerciseResponse from(ExternalExercise exercise) {
        return new ExerciseResponse(exercise.externalId(), exercise.name(), exercise.gifUrl(),
                exercise.primaryMuscleGroups(), exercise.secondaryMuscleGroups());
    }
}
