package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.model.Plan;
import java.time.LocalDateTime;

public record PlanListItemResponse(Long id, String name,
        LocalDateTime updatedAt, int exerciseCount) {

    public static PlanListItemResponse from(Plan plan, int exerciseCount) {
        return new PlanListItemResponse(plan.getId(), plan.getName(),
                plan.getUpdatedAt(), exerciseCount);
    }
}
