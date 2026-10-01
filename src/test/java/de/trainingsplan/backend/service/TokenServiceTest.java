package de.trainingsplan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.trainingsplan.backend.model.AuthToken;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.AuthTokenRepository;
import de.trainingsplan.backend.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TokenServiceTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(new User("max", "max@mosbach.de", "hash"));
    }

    @Test
    void newTokenIsValidAndExpiresInTheFuture() {
        String token = tokenService.createToken(user);

        assertThat(tokenService.findUserByToken(token)).hasValueSatisfying(u ->
                assertThat(u.getId()).isEqualTo(user.getId()));
        AuthToken stored = authTokenRepository.findAll().get(0);
        assertThat(stored.getExpiresAt()).isAfter(stored.getCreatedAt().plus(Duration.ofHours(23)));
    }

    @Test
    void expiredTokenIsNotAccepted() {
        String token = "expired-token";
        authTokenRepository.save(new AuthToken(TokenService.hash(token), user,
                Instant.now().minusSeconds(1)));

        assertThat(tokenService.findUserByToken(token)).isEmpty();
    }

    @Test
    void unknownOrEmptyTokenIsNotAccepted() {
        assertThat(tokenService.findUserByToken("nope")).isEmpty();
        assertThat(tokenService.findUserByToken("")).isEmpty();
        assertThat(tokenService.findUserByToken(null)).isEmpty();
    }

    @Test
    void creatingTokenRemovesExpiredOnes() {
        authTokenRepository.save(new AuthToken(TokenService.hash("old"), user,
                Instant.now().minusSeconds(60)));

        tokenService.createToken(user);

        assertThat(authTokenRepository.findAll()).hasSize(1);
        assertThat(authTokenRepository.findByTokenHash(TokenService.hash("old"))).isEmpty();
    }
}
