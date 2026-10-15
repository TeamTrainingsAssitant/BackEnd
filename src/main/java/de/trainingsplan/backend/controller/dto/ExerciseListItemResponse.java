package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.service.ExerciseDbClient.ExternalExercise;
import java.util.List;

public record ExerciseListItemResponse(String externalId, String name, String gifUrl,
        List<String> primaryMuscleGroups, List<String> secondaryMuscleGroups,
        List<String> bodyParts, List<String> equipments) {

    public static ExerciseListItemResponse from(ExternalExercise exercise) {
        return new ExerciseListItemResponse(exercise.externalId(), exercise.name(), exercise.gifUrl(),
                exercise.primaryMuscleGroups(), exercise.secondaryMuscleGroups(),
                exercise.bodyParts(), exercise.equipments());
    }
}
