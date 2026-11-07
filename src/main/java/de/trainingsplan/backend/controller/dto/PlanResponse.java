package de.trainingsplan.backend.controller.dto;

import java.time.LocalDateTime;

import de.trainingsplan.backend.model.Plan;

public record PlanResponse(Long id, String name,
        LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getName(),
                plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
