package de.trainingsplan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.LoginRequest;
import de.trainingsplan.backend.controller.dto.LoginResponse;
import de.trainingsplan.backend.controller.dto.LogoutRequest;
import de.trainingsplan.backend.controller.dto.MessageResponse;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthControllerTest {

    private AuthService authService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        controller = new AuthController(authService);
    }

    @Test
    void loginReturnsMessageAndToken() {
        when(authService.login("max@mosbach.de", "super-safe")).thenReturn("the-token");

        LoginResponse response = controller.login(new LoginRequest("max@mosbach.de", "super-safe"));

        assertThat(response.message()).isEqualTo("User logged on.");
        assertThat(response.token()).isEqualTo("the-token");
    }

    @Test
    void loginPassesServiceErrorThrough() {
        when(authService.login("max@mosbach.de", "wrong"))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Password wrong."));

        assertThatThrownBy(() -> controller.login(new LoginRequest("max@mosbach.de", "wrong")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Password wrong.");
                });
    }

    @Test
    void logoutRevokesTokenAndReturnsMessage() {
        MessageResponse response = controller.logout(new LogoutRequest("the-token"));

        verify(authService).logout("the-token");
        assertThat(response.message()).isEqualTo("User logged off.");
    }

    @Test
    void logoutPassesServiceErrorThrough() {
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "Token wrong."))
                .when(authService).logout("unknown");

        assertThatThrownBy(() -> controller.logout(new LogoutRequest("unknown")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Token wrong."));
    }
}
