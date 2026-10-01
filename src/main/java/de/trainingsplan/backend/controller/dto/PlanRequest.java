package de.trainingsplan.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record PlanRequest (@NotBlank (message= "name is not allowed to be empty") String name, String description) {

}
