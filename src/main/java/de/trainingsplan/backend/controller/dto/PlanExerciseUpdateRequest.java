package de.trainingsplan.backend.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;

public record PlanExerciseUpdateRequest(@Size(max = 500) String note,
        @DecimalMin("0") @DecimalMax("1000") Double weight, @Min(1) @Max(20) Integer sets,
        @Min(1) @Max(100) Integer reps, @Size(max = 20) List<@Valid TrainingSetRequest> trainingSets) {

    public PlanExerciseUpdateRequest(String note, Double weight, Integer sets, Integer reps) {
        this(note, weight, sets, reps, null);
    }

}
