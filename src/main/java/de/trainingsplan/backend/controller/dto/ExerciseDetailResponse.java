package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.service.ExerciseDbClient.ExternalExercise;
import java.util.List;

public record ExerciseDetailResponse(String externalId, String name, String gifUrl,
        List<String> primaryMuscleGroups, List<String> secondaryMuscleGroups,
        List<String> bodyParts, List<String> equipments, List<String> instructions) {

    public static ExerciseDetailResponse from(ExternalExercise exercise) {
        return new ExerciseDetailResponse(exercise.externalId(), exercise.name(), exercise.gifUrl(),
                exercise.primaryMuscleGroups(), exercise.secondaryMuscleGroups(),
                exercise.bodyParts(), exercise.equipments(), exercise.instructions());
    }
}
