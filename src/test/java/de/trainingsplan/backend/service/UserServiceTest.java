package de.trainingsplan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.RegisterRequest;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void assertRejected(RegisterRequest request, HttpStatus status, String message) {
        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(status);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerStoresBcryptHashAndNormalizedEmail() {
        userService.register(new RegisterRequest(" max ", " Max@Mosbach.de ", "super-safe"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getUsername()).isEqualTo("max");
        assertThat(user.getEmail()).isEqualTo("max@mosbach.de");
        assertThat(user.getPasswordHash()).isNotEqualTo("super-safe").startsWith("$2");
        assertThat(passwordEncoder.matches("super-safe", user.getPasswordHash())).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateEmailIsConflict() {
        when(userRepository.existsByEmail("max@mosbach.de")).thenReturn(true);

        assertRejected(new RegisterRequest("max", "MAX@mosbach.de", "super-safe"),
                HttpStatus.CONFLICT, "Email already registered.");
    }

    @Test
    void raceOnUniqueConstraintIsConflict() {
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> userService.register(new RegisterRequest("max", "max@mosbach.de", "super-safe")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Email already registered.");
                });
    }

    @Test
    void invalidEmailIsBadRequest() {
        assertRejected(new RegisterRequest("max", "not-an-email", "super-safe"),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
        assertRejected(new RegisterRequest("max", null, "super-safe"),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
    }

    @Test
    void shortOrMissingPasswordIsBadRequest() {
        assertRejected(new RegisterRequest("max", "max@mosbach.de", "short"),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
        assertRejected(new RegisterRequest("max", "max@mosbach.de", null),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
    }

    @Test
    void passwordOverBcryptLimitIsBadRequest() {
        assertRejected(new RegisterRequest("max", "max@mosbach.de", "a".repeat(73)),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
    }

    @Test
    void missingUsernameIsBadRequest() {
        assertRejected(new RegisterRequest(" ", "max@mosbach.de", "super-safe"),
                HttpStatus.BAD_REQUEST, "Username missing.");
        assertRejected(new RegisterRequest(null, "max@mosbach.de", "super-safe"),
                HttpStatus.BAD_REQUEST, "Username missing.");
        assertRejected(null, HttpStatus.BAD_REQUEST, "Username missing.");
    }
}
