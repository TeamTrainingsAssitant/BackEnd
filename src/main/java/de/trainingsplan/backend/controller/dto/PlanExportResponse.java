package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.PlanExercise;
import java.time.LocalDateTime;
import java.util.List;

/** Ein Plan mit allen Übungen (in Reihenfolge) für den Datenexport. */
public record PlanExportResponse(Long id, String name, String description,
        LocalDateTime createdAt, LocalDateTime updatedAt, List<PlanExerciseResponse> exercises) {

    public static PlanExportResponse from(Plan plan, List<PlanExercise> exercises) {
        return new PlanExportResponse(plan.getId(), plan.getName(), plan.getDescription(),
                plan.getCreatedAt(), plan.getUpdatedAt(),
                exercises.stream().map(PlanExerciseResponse::from).toList());
    }
}
