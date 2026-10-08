package de.trainingsplan.backend.service;

import de.trainingsplan.backend.controller.dto.PlanExerciseRequest;
import de.trainingsplan.backend.controller.dto.PlanExerciseUpdateRequest;
import de.trainingsplan.backend.controller.dto.PlanRequest;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.PlanExercise;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.PlanExerciseRepository;
import de.trainingsplan.backend.repository.PlanRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final PlanRepository planRepository;

    private final PlanExerciseRepository planExerciseRepository;
    private final ExerciseDbClient exerciseDbClient;

    public PlanService(PlanRepository planRepository, PlanExerciseRepository planExerciseRepository,
            ExerciseDbClient exerciseDbClient) {
        this.planRepository = planRepository;
        this.planExerciseRepository = planExerciseRepository;
        this.exerciseDbClient = exerciseDbClient;
    }

    @Transactional(readOnly = true)
    public List<Plan> list(User user) {
        return planRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
    }

    @Transactional
    public Plan create(User user, PlanRequest request) {
        return planRepository.save(new Plan(user, request.name().trim(), request.description()));
    }

    @Transactional(readOnly = true)
    public Plan get(User user, Long planId) {
        return findOwnPlan(user, planId);
    }

    @Transactional
    public Plan update(User user, Long planId, PlanRequest request) {
        Plan plan = findOwnPlan(user, planId);
        plan.update(request.name().trim(), request.description());
        return plan;
    }

    @Transactional
    public void delete(User user, Long planId) {
        planRepository.delete(findOwnPlan(user, planId));
    }

    /** Hängt eine Übung hinten an den Plan an. Name, gifUrl und Muskeln holt das Backend von ExerciseDB. */
    @Transactional
    public PlanExercise addExercise(User user, Long planId, PlanExerciseRequest request) {
        Plan plan = findOwnPlan(user, planId);
        ExerciseDbClient.ExternalExercise external = exerciseDbClient.getExercise(request.externalId().trim());
        int position = planExerciseRepository.countByPlanId(planId) + 1;
        PlanExercise exercise = new PlanExercise(plan, external.externalId(), external.name(), external.gifUrl(),
                external.primaryMuscleGroups(), external.secondaryMuscleGroups(), position, request.note());
        plan.touch();
        return planExerciseRepository.save(exercise);
    }

    /** Ändert die Note einer Übung im Plan, null entfernt sie. */
    @Transactional
    public PlanExercise updateExercise(User user, Long planId, Long planExerciseId,
            PlanExerciseUpdateRequest request) {
        Plan plan = findOwnPlan(user, planId);
        PlanExercise exercise = planExerciseRepository.findByIdAndPlanId(planExerciseId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Exercise not found."));
        exercise.updateNote(request.note());
        plan.touch();
        return exercise;
    }

    /** Fremde und unbekannte Pläne verhalten sich gleich: 404. */
    private Plan findOwnPlan(User user, Long planId) {
        return planRepository.findByIdAndUserId(planId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));
    }
}
