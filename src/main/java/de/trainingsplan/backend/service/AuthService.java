package de.trainingsplan.backend.service;

import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.UserRepository;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** Gibt ein neues Token zurück. Unbekannte E-Mail und falsches Passwort sind nicht unterscheidbar. */
    public String login(String email, String password) {
        if (email == null || password == null) {
            throw passwordWrong();
        }
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(AuthService::passwordWrong);
        return tokenService.createToken(user);
    }

    public void logout(String token) {
        if (!tokenService.revokeToken(token)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Token wrong.");
        }
    }

    private static ApiException passwordWrong() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Password wrong.");
    }
}
