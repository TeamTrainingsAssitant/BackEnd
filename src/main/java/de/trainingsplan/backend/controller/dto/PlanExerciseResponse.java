package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.model.PlanExercise;
import java.util.List;

public record PlanExerciseResponse(Long id, String externalId, String name, String gifUrl,
        List<String> primaryMuscleGroups, List<String> secondaryMuscleGroups,
        int position, String note, Double weight, Integer sets, Integer reps) {

    public static PlanExerciseResponse from(PlanExercise exercise) {
        return new PlanExerciseResponse(exercise.getId(), exercise.getExternalId(), exercise.getName(),
                exercise.getGifUrl(), exercise.getPrimaryMuscleGroups(), exercise.getSecondaryMuscleGroups(),
                exercise.getPosition(), exercise.getNote(), exercise.getWeight(), exercise.getSets(),
                exercise.getReps());
    }
}
