package de.trainingsplan.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlanExerciseRequest(@NotBlank String externalId, @Size(max = 500) String note) {

}
