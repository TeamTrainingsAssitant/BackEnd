package de.trainingsplan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String username, String email, String password) {
        return """
                {"username":"%s","email":"%s","password":"%s"}""".formatted(username, email, password);
    }

    @Test
    void registerStoresBcryptHashNotPlaintext() throws Exception {
        register(body("max", "Max@Mosbach.de", "super-safe"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User created."))
                .andExpect(jsonPath("$.userId").isNumber());

        User saved = userRepository.findByEmail("max@mosbach.de").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("super-safe").startsWith("$2");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateEmailIsConflictEvenWithDifferentCase() throws Exception {
        register(body("max", "max@mosbach.de", "super-safe")).andExpect(status().isCreated());

        register(body("max2", "MAX@mosbach.de", "super-safe"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered."));
    }

    @Test
    void invalidEmailIsBadRequest() throws Exception {
        register(body("max", "not-an-email", "super-safe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email or password invalid."));
    }

    @Test
    void shortPasswordIsBadRequest() throws Exception {
        register(body("max", "max@mosbach.de", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email or password invalid."));
    }

    @Test
    void missingUsernameIsBadRequest() throws Exception {
        register("{\"email\":\"max@mosbach.de\",\"password\":\"super-safe\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username missing."));
    }

    @Test
    void malformedJsonUsesErrorFormat() throws Exception {
        register("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request."));
    }
}
