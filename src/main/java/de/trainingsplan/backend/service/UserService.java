package de.trainingsplan.backend.service;

import de.trainingsplan.backend.controller.dto.RegisterRequest;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    static final int MIN_PASSWORD_LENGTH = 8;
    // BCrypt verarbeitet maximal 72 Bytes
    static final int MAX_PASSWORD_BYTES = 72;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username missing.");
        }
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        String password = request.password();
        if (!EMAIL.matcher(email).matches()
                || password == null
                || password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email or password invalid.");
        }
        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }
        try {
            return userRepository.saveAndFlush(
                    new User(request.username().trim(), email, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // parallele Registrierung mit derselben E-Mail
            throw emailTaken();
        }
    }

    private ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "Email already registered.");
    }
}
