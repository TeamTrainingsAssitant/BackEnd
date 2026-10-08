package de.trainingsplan.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Nur der PasswordEncoder: Die SecurityFilterChain (öffentliche Routen, 401-Antwort) bräuchte einen
 * Spring-Kontext und wird deshalb bewusst nicht als Unit-Test geprüft.
 */
class SecurityConfigTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void passwordEncoderIsBcrypt() {
        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(encoder.encode("super-safe")).startsWith("$2");
    }

    @Test
    void encodedPasswordIsNotPlaintextAndMatchesOnlyTheOriginal() {
        String hash = encoder.encode("super-safe");

        assertThat(hash).isNotEqualTo("super-safe");
        assertThat(encoder.matches("super-safe", hash)).isTrue();
        assertThat(encoder.matches("Super-safe", hash)).isFalse();
        assertThat(encoder.matches("", hash)).isFalse();
    }

    @Test
    void samePasswordGetsDifferentHashesBecauseOfSalt() {
        assertThat(encoder.encode("super-safe")).isNotEqualTo(encoder.encode("super-safe"));
    }
}
