package de.trainingsplan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.model.AuthToken;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.AuthTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenServiceTest {

    private static final Duration TTL = Duration.ofHours(1);

    private AuthTokenRepository authTokenRepository;
    private TokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        authTokenRepository = mock(AuthTokenRepository.class);
        tokenService = new TokenService(authTokenRepository, TTL);
        user = new User("max", "max@mosbach.de", "hash");
    }

    @Test
    void createTokenStoresOnlyHashWithExpiry() {
        Instant before = Instant.now();

        String token = tokenService.createToken(user);

        ArgumentCaptor<AuthToken> saved = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(saved.capture());
        AuthToken stored = saved.getValue();
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        assertThat(stored.getTokenHash()).isEqualTo(TokenService.hash(token)).isNotEqualTo(token);
        assertThat(stored.getUser()).isSameAs(user);
        assertThat(stored.getExpiresAt()).isBetween(before.plus(TTL), Instant.now().plus(TTL));
    }

    @Test
    void createTokenGeneratesDifferentTokens() {
        assertThat(tokenService.createToken(user)).isNotEqualTo(tokenService.createToken(user));
    }

    @Test
    void createTokenRemovesExpiredTokens() {
        tokenService.createToken(user);

        verify(authTokenRepository).deleteByExpiresAtBefore(any(Instant.class));
    }

    @Test
    void findUserByTokenReturnsUserOfValidToken() {
        AuthToken stored = new AuthToken(TokenService.hash("abc"), user, Instant.now().plus(TTL));
        when(authTokenRepository.findByTokenHashAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq(TokenService.hash("abc")), any(Instant.class)))
                .thenReturn(Optional.of(stored));

        assertThat(tokenService.findUserByToken("abc")).containsSame(user);
    }

    @Test
    void findUserByTokenIsEmptyForUnknownOrExpiredToken() {
        when(authTokenRepository.findByTokenHashAndExpiresAtAfter(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThat(tokenService.findUserByToken("nope")).isEmpty();
    }

    @Test
    void findUserByTokenIgnoresBlankToken() {
        assertThat(tokenService.findUserByToken("")).isEmpty();
        assertThat(tokenService.findUserByToken("  ")).isEmpty();
        assertThat(tokenService.findUserByToken(null)).isEmpty();
        verifyNoInteractions(authTokenRepository);
    }

    @Test
    void revokeTokenReportsWhetherATokenWasDeleted() {
        when(authTokenRepository.deleteByTokenHash(TokenService.hash("abc"))).thenReturn(1L);
        when(authTokenRepository.deleteByTokenHash(TokenService.hash("unknown"))).thenReturn(0L);

        assertThat(tokenService.revokeToken("abc")).isTrue();
        assertThat(tokenService.revokeToken("unknown")).isFalse();
    }

    @Test
    void revokeTokenIgnoresBlankToken() {
        assertThat(tokenService.revokeToken(null)).isFalse();
        assertThat(tokenService.revokeToken(" ")).isFalse();
        verify(authTokenRepository, never()).deleteByTokenHash(anyString());
    }
}
