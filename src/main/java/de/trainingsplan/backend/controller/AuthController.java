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

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public LoginResponse login(@RequestBody LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        return new LoginResponse("User logged on.", token);
    }

    @DeleteMapping
    public MessageResponse logout(@RequestBody LogoutRequest request) {
        authService.logout(request.token());
        return new MessageResponse("User logged off.");
    }
}
