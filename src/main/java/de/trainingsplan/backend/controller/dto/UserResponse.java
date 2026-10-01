package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.model.User;
import java.time.LocalDate;

public record UserResponse(Long userId, String username, String email, LocalDate createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }
}
