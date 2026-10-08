package de.trainingsplan.backend.repository;

import de.trainingsplan.backend.model.PlanExercise;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanExerciseRepository extends JpaRepository<PlanExercise, Long> {

    /** Anzahl der Übungen in einem Plan, daraus ergibt sich die nächste Position. */
    int countByPlanId(Long planId);
}
