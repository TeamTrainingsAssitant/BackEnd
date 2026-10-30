package de.trainingsplan.backend.controller;

import de.trainingsplan.backend.controller.dto.LoginRequest;
import de.trainingsplan.backend.controller.dto.LoginResponse;
import de.trainingsplan.backend.controller.dto.LogoutRequest;
import de.trainingsplan.backend.controller.dto.MessageResponse;
import de.trainingsplan.backend.service.AuthService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
                               HttpServletResponse response) {
        String token = authService.login(request.email(), request.password());
        response.addHeader("Set-Cookie", sessionCookie(token, isSecureRequest(httpRequest)));
        return new LoginResponse("User logged on.", null);
    }

    @DeleteMapping
    public MessageResponse logout(@RequestBody(required = false) LogoutRequest request,
                                  HttpServletRequest httpRequest, HttpServletResponse response) {
        String token = request == null ? null : request.token();
        if (token == null && httpRequest.getCookies() != null) {
            for (Cookie cookie : httpRequest.getCookies()) {
                if ("capsule_session".equals(cookie.getName())) token = cookie.getValue();
            }
        }
        if (token != null) authService.logout(token);
        response.addHeader("Set-Cookie", "capsule_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0" + (isSecureRequest(httpRequest) ? "; Secure" : ""));
        return new MessageResponse("User logged off.");
    }

    private static String sessionCookie(String token, boolean secure) {
        return "capsule_session=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400" + (secure ? "; Secure" : "");
    }

    private static boolean isSecureRequest(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
