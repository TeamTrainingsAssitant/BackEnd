package de.trainingsplan.backend.controller;

import de.trainingsplan.backend.controller.dto.ExerciseResponse;
import de.trainingsplan.backend.service.ExerciseDbClient;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import de.trainingsplan.backend.model.User;

@RestController
@RequestMapping("/exercises")
public class ExerciseController {

    private final ExerciseDbClient exerciseDbClient;

    public ExerciseController(ExerciseDbClient exerciseDbClient) {
        this.exerciseDbClient = exerciseDbClient;
    }

    @GetMapping
    public List<ExerciseResponse> search(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return exerciseDbClient.searchExercises(name, safeLimit).stream()
                .map(ExerciseResponse::from)
                .toList();
    }
}
