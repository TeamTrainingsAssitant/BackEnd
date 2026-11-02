package de.trainingsplan.backend.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;

public record PlanExerciseRequest(@NotBlank String externalId, @Size(max = 500) String note,
        @DecimalMin("0") @DecimalMax("1000") Double weight, @Min(1) @Max(20) Integer sets,
        @Min(1) @Max(100) Integer reps, @Size(min = 1, max = 20) List<@Valid TrainingSetRequest> trainingSets) {

    public PlanExerciseRequest(String externalId, String note, Double weight, Integer sets, Integer reps) {
        this(externalId, note, weight, sets, reps, null);
    }

    @AssertTrue(message = "Legacy training values must include weight, sets, and reps.")
    public boolean isLegacyTrainingComplete() {
        boolean noneProvided = weight == null && sets == null && reps == null;
        boolean allProvided = weight != null && sets != null && reps != null;
        boolean invalidField = weight != null && (weight < 0 || weight > 1000)
                || sets != null && (sets < 1 || sets > 20)
                || reps != null && (reps < 1 || reps > 100);
        return trainingSets != null || noneProvided || allProvided || invalidField;
    }

}
