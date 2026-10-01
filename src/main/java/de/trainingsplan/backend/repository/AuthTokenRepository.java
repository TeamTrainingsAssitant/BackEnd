package de.trainingsplan.backend.repository;

import de.trainingsplan.backend.model.AuthToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    /** Nur Tokens, die zum übergebenen Zeitpunkt noch nicht abgelaufen sind. */
    Optional<AuthToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    long deleteByExpiresAtBefore(Instant now);

    long deleteByTokenHash(String tokenHash);
}
