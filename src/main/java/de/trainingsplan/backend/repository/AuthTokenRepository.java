package de.trainingsplan.backend.repository;

import de.trainingsplan.backend.model.AuthToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    long deleteByTokenHash(String tokenHash);
}
