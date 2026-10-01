package de.trainingsplan.backend.service;

import de.trainingsplan.backend.controller.dto.PlanRequest;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.PlanRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
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

    /** Fremde und unbekannte Pläne verhalten sich gleich: 404. */
    private Plan findOwnPlan(User user, Long planId) {
        return planRepository.findByIdAndUserId(planId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));
    }
}
