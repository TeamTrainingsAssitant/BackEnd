package de.trainingsplan.backend.controller;

import de.trainingsplan.backend.controller.dto.RegisterRequest;
import de.trainingsplan.backend.controller.dto.RegisterResponse;
import de.trainingsplan.backend.controller.dto.UserResponse;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse("User created.", user.getId()));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return UserResponse.from(user);
    }
}
