package de.trainingsplan.backend.repository;

import de.trainingsplan.backend.model.PlanExercise;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanExerciseRepository extends JpaRepository<PlanExercise, Long> {

    /** Anzahl der Übungen in einem Plan, daraus ergibt sich die nächste Position. */
    int countByPlanId(Long planId);

    /** Liefert die Übung nur, wenn sie zu diesem Plan gehört (sonst leer, also 404). */
    Optional<PlanExercise> findByIdAndPlanId(Long id, Long planId);
}
