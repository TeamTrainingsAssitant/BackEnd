package de.trainingsplan.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record PlanExerciseReplacementRequest(@NotBlank String externalId) {
}
