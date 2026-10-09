package de.trainingsplan.backend.controller.dto;

import java.util.List;

public record UserExportResponse(UserResponse user, List<PlanExportResponse> plans) {
}
