package de.trainingsplan.backend.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PlanExerciseOrderRequest(@NotNull List<@NotNull Long> exerciseIds) {
}
