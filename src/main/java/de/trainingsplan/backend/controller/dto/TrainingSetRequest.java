package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.model.TrainingSet;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TrainingSetRequest(@NotNull @DecimalMin("0") @DecimalMax("1000") Double weight,
        @NotNull @Min(1) @Max(100) Integer reps, Boolean completed) {

    public TrainingSetRequest(Double weight, Integer reps) {
        this(weight, reps, false);
    }

    public TrainingSet toModel() {
        // Completion is no longer a user feature; accept the old backend field during deploy skew.
        return new TrainingSet(weight, reps);
    }
}
