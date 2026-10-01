package de.trainingsplan.backend.service;

import de.trainingsplan.backend.model.AuthToken;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.AuthTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final AuthTokenRepository authTokenRepository;

    public TokenService(AuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    /** Erzeugt ein neues Token für den User und gibt es im Klartext zurück (nur hier einsehbar). */
    @Transactional
    public String createToken(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        authTokenRepository.save(new AuthToken(hash(token), user));
        return token;
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
