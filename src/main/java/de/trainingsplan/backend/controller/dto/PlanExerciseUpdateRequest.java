package de.trainingsplan.backend.controller.dto;

import jakarta.validation.constraints.Size;

public record PlanExerciseUpdateRequest(@Size(max = 500) String note) {

}
