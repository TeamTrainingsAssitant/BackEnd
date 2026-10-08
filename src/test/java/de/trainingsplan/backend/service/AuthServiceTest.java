package de.trainingsplan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserRepository userRepository;
    private TokenService tokenService;
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenService = mock(TokenService.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
        user = new User("max", "max@mosbach.de", passwordEncoder.encode("super-safe"));
        when(userRepository.findByEmail("max@mosbach.de")).thenReturn(Optional.of(user));
    }

    private void assertBadRequest(Runnable action, String message) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() {
        when(tokenService.createToken(user)).thenReturn("the-token");

        assertThat(authService.login("max@mosbach.de", "super-safe")).isEqualTo("the-token");
    }

    @Test
    void loginNormalizesEmail() {
        when(tokenService.createToken(user)).thenReturn("the-token");

        assertThat(authService.login("  MAX@Mosbach.de ", "super-safe")).isEqualTo("the-token");
    }

    @Test
    void wrongPasswordIsBadRequestAndCreatesNoToken() {
        assertBadRequest(() -> authService.login("max@mosbach.de", "wrong"), "Password wrong.");
        verify(tokenService, never()).createToken(user);
    }

    @Test
    void unknownEmailGivesSameErrorAsWrongPassword() {
        when(userRepository.findByEmail("nobody@mosbach.de")).thenReturn(Optional.empty());

        assertBadRequest(() -> authService.login("nobody@mosbach.de", "super-safe"), "Password wrong.");
    }

    @Test
    void missingCredentialsAreBadRequest() {
        assertBadRequest(() -> authService.login(null, "super-safe"), "Password wrong.");
        assertBadRequest(() -> authService.login("max@mosbach.de", null), "Password wrong.");
    }

    @Test
    void logoutRevokesToken() {
        when(tokenService.revokeToken("the-token")).thenReturn(true);

        authService.logout("the-token");

        verify(tokenService).revokeToken("the-token");
    }

    @Test
    void logoutWithUnknownTokenIsBadRequest() {
        when(tokenService.revokeToken("unknown")).thenReturn(false);

        assertBadRequest(() -> authService.logout("unknown"), "Token wrong.");
    }

    @Test
    void emptyEmailOrPasswordIsBadRequest() {
        assertBadRequest(() -> authService.login("", "super-safe"), "Password wrong.");
        assertBadRequest(() -> authService.login("max@mosbach.de", ""), "Password wrong.");
        verify(tokenService, never()).createToken(any());
    }

    @Test
    void unknownEmailCreatesNoToken() {
        assertBadRequest(() -> authService.login("nobody@mosbach.de", "super-safe"), "Password wrong.");

        verify(tokenService, never()).createToken(any());
    }

    @Test
    void passwordIsNotTrimmedAtLogin() {
        assertBadRequest(() -> authService.login("max@mosbach.de", "super-safe "), "Password wrong.");
        assertBadRequest(() -> authService.login("max@mosbach.de", " super-safe"), "Password wrong.");
    }

    @Test
    void passwordIsCaseSensitive() {
        assertBadRequest(() -> authService.login("max@mosbach.de", "SUPER-SAFE"), "Password wrong.");
    }

    @Test
    void logoutWithMissingOrEmptyTokenIsBadRequest() {
        assertBadRequest(() -> authService.logout(null), "Token wrong.");
        assertBadRequest(() -> authService.logout(""), "Token wrong.");
    }
}
