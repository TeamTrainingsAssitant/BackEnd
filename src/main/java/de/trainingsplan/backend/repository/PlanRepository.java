package de.trainingsplan.backend.repository;

import de.trainingsplan.backend.model.Plan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    /** Alle Pläne eines Benutzers, zuletzt bearbeitete zuerst. */
    List<Plan> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /** Liefert den Plan nur, wenn er dem Benutzer gehört (sonst leer, also 404). */
    Optional<Plan> findByIdAndUserId(Long id, Long userId);
}
