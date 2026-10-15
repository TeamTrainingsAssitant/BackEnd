package de.trainingsplan.backend.controller.dto;

import java.util.List;

public record ExerciseListResponse(int total, List<ExerciseListItemResponse> exercises) {
}
