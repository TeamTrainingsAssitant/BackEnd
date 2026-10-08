package de.trainingsplan.backend.controller;

import de.trainingsplan.backend.controller.dto.MessageResponse;
import de.trainingsplan.backend.controller.dto.PlanExerciseRequest;
import de.trainingsplan.backend.controller.dto.PlanExerciseResponse;
import de.trainingsplan.backend.controller.dto.PlanExerciseUpdateRequest;
import de.trainingsplan.backend.controller.dto.PlanListItemResponse;
import de.trainingsplan.backend.controller.dto.PlanRequest;
import de.trainingsplan.backend.controller.dto.PlanResponse;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.service.PlanService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public List<PlanListItemResponse> list(@AuthenticationPrincipal User user) {
        // exerciseCount ist 0, bis es Übungen in Plänen gibt
        return planService.list(user).stream()
                .map(plan -> PlanListItemResponse.from(plan, 0))
                .toList();
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@AuthenticationPrincipal User user,
            @Valid @RequestBody PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PlanResponse.from(planService.create(user, request)));
    }

    @GetMapping("/{planId}")
    public PlanResponse get(@AuthenticationPrincipal User user, @PathVariable Long planId) {
        return PlanResponse.from(planService.get(user, planId));
    }

    @PutMapping("/{planId}")
    public PlanResponse update(@AuthenticationPrincipal User user, @PathVariable Long planId,
            @Valid @RequestBody PlanRequest request) {
        return PlanResponse.from(planService.update(user, planId, request));
    }

    @DeleteMapping("/{planId}")
    public MessageResponse delete(@AuthenticationPrincipal User user, @PathVariable Long planId) {
        planService.delete(user, planId);
        return new MessageResponse("Plan deleted.");
    }

    @PostMapping("/{planId}/exercises")
    public ResponseEntity<PlanExerciseResponse> addExercise(@AuthenticationPrincipal User user,
            @PathVariable Long planId, @Valid @RequestBody PlanExerciseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PlanExerciseResponse.from(planService.addExercise(user, planId, request)));
    }

    @PutMapping("/{planId}/exercises/{planExerciseId}")
    public PlanExerciseResponse updateExercise(@AuthenticationPrincipal User user, @PathVariable Long planId,
            @PathVariable Long planExerciseId, @Valid @RequestBody PlanExerciseUpdateRequest request) {
        return PlanExerciseResponse.from(planService.updateExercise(user, planId, planExerciseId, request));
    }
}
