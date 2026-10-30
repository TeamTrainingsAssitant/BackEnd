package de.trainingsplan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.LoginRequest;
import de.trainingsplan.backend.controller.dto.LoginResponse;
import de.trainingsplan.backend.controller.dto.LogoutRequest;
import de.trainingsplan.backend.controller.dto.MessageResponse;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {

    private AuthService authService;
    private AuthController controller;
    private MockHttpServletRequest httpRequest;
    private MockHttpServletResponse httpResponse;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        controller = new AuthController(authService);
        httpRequest = new MockHttpServletRequest();
        httpResponse = new MockHttpServletResponse();
    }

    @Test
    void loginSetsSessionCookieAndOmitsTokenFromBody() {
        when(authService.login("max@mosbach.de", "super-safe")).thenReturn("the-token");

        LoginResponse response = controller.login(new LoginRequest("max@mosbach.de", "super-safe"),
                httpRequest, httpResponse);

        assertThat(response.message()).isEqualTo("User logged on.");
        assertThat(response.token()).isNull();
        Cookie cookie = httpResponse.getCookie("capsule_session");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo("the-token");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(86400);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(httpResponse.getHeader("Set-Cookie")).contains("SameSite=Lax");
    }

    @Test
    void loginMarksCookieSecureOnHttpsRequest() {
        when(authService.login("max@mosbach.de", "super-safe")).thenReturn("the-token");
        httpRequest.setSecure(true);

        controller.login(new LoginRequest("max@mosbach.de", "super-safe"), httpRequest, httpResponse);

        assertThat(httpResponse.getCookie("capsule_session").getSecure()).isTrue();
    }

    @Test
    void loginMarksCookieSecureBehindHttpsProxy() {
        when(authService.login("max@mosbach.de", "super-safe")).thenReturn("the-token");
        httpRequest.addHeader("X-Forwarded-Proto", "https");

        controller.login(new LoginRequest("max@mosbach.de", "super-safe"), httpRequest, httpResponse);

        assertThat(httpResponse.getCookie("capsule_session").getSecure()).isTrue();
    }

    @Test
    void loginPassesServiceErrorThrough() {
        when(authService.login("max@mosbach.de", "wrong"))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Password wrong."));

        assertThatThrownBy(() -> controller.login(new LoginRequest("max@mosbach.de", "wrong"),
                httpRequest, httpResponse))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Password wrong.");
                });
        assertThat(httpResponse.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void logoutWithTokenInBodyRevokesTokenAndClearsCookie() {
        MessageResponse response = controller.logout(new LogoutRequest("the-token"), httpRequest, httpResponse);

        verify(authService).logout("the-token");
        assertThat(response.message()).isEqualTo("User logged off.");
        Cookie cleared = httpResponse.getCookie("capsule_session");
        assertThat(cleared).isNotNull();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.isHttpOnly()).isTrue();
    }

    @Test
    void logoutFallsBackToSessionCookie() {
        httpRequest.setCookies(new Cookie("other", "x"), new Cookie("capsule_session", "cookie-token"));

        MessageResponse response = controller.logout(null, httpRequest, httpResponse);

        verify(authService).logout("cookie-token");
        assertThat(response.message()).isEqualTo("User logged off.");
    }

    @Test
    void logoutPrefersTokenFromBodyOverCookie() {
        httpRequest.setCookies(new Cookie("capsule_session", "cookie-token"));

        controller.logout(new LogoutRequest("body-token"), httpRequest, httpResponse);

        verify(authService).logout("body-token");
    }

    @Test
    void logoutWithoutAnyTokenStillClearsCookie() {
        MessageResponse response = controller.logout(null, httpRequest, httpResponse);

        verify(authService, never()).logout(org.mockito.ArgumentMatchers.anyString());
        assertThat(response.message()).isEqualTo("User logged off.");
        assertThat(httpResponse.getCookie("capsule_session").getMaxAge()).isZero();
    }

    @Test
    void logoutMarksClearingCookieSecureOnHttpsRequest() {
        httpRequest.setSecure(true);

        controller.logout(null, httpRequest, httpResponse);

        assertThat(httpResponse.getCookie("capsule_session").getSecure()).isTrue();
    }

    @Test
    void logoutPassesServiceErrorThrough() {
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "Token wrong."))
                .when(authService).logout("unknown");

        assertThatThrownBy(() -> controller.logout(new LogoutRequest("unknown"), httpRequest, httpResponse))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Token wrong."));
    }
}
