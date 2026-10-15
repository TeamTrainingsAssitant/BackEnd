package de.trainingsplan.backend.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record PlanExerciseUpdateRequest(@Size(max = 500) String note,
        @DecimalMin("0") @DecimalMax("1000") Double weight, @Min(1) @Max(20) Integer sets,
        @Min(1) @Max(100) Integer reps) {

}
