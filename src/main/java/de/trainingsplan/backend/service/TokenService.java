package de.trainingsplan.backend.service;

import de.trainingsplan.backend.model.AuthToken;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.AuthTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final AuthTokenRepository authTokenRepository;
    private final Duration tokenTtl;

    public TokenService(AuthTokenRepository authTokenRepository,
                        @Value("${app.auth.token-ttl:24h}") Duration tokenTtl) {
        this.authTokenRepository = authTokenRepository;
        this.tokenTtl = tokenTtl;
    }

    /**
     * Erzeugt ein neues Token für den User und gibt es im Klartext zurück (nur hier einsehbar).
     * Das Token ist {@code app.auth.token-ttl} lang gültig. Dabei werden abgelaufene Tokens aufgeräumt.
     */
    @Transactional
    public String createToken(User user) {
        Instant now = Instant.now();
        authTokenRepository.deleteByExpiresAtBefore(now);
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        authTokenRepository.save(new AuthToken(hash(token), user, now.plus(tokenTtl)));
        return token;
    }

    /** Liefert den zugehörigen User, wenn das Token existiert und noch nicht abgelaufen ist. */
    @Transactional(readOnly = true)
    public Optional<User> findUserByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return authTokenRepository.findByTokenHashAndExpiresAtAfter(hash(token), Instant.now())
                .map(AuthToken::getUser);
    }

    /** Macht das Token ungültig. Gibt false zurück, wenn es nicht existiert. */
    @Transactional
    public boolean revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return authTokenRepository.deleteByTokenHash(hash(token)) > 0;
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
