package de.trainingsplan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.trainingsplan.backend.model.AuthToken;
import de.trainingsplan.backend.repository.AuthTokenRepository;
import de.trainingsplan.backend.repository.UserRepository;
import java.util.List;
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
class AuthControllerTest {

    private static final String EMAIL = "max@mosbach.de";
    private static final String PASSWORD = "super-safe";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @BeforeEach
    void setUp() throws Exception {
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"max\",\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions logout(String token) throws Exception {
        return mockMvc.perform(delete("/auth").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private String loginToken() throws Exception {
        String json = login(EMAIL, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void loginReturnsTokenAndStoresOnlyItsHash() throws Exception {
        login("MAX@mosbach.de", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User logged on."))
                .andExpect(jsonPath("$.token").isNotEmpty());

        String token = loginToken();
        List<AuthToken> stored = authTokenRepository.findAll();
        assertThat(stored).hasSize(2);
        assertThat(stored).noneMatch(t -> t.getTokenHash().equals(token));
    }

    @Test
    void wrongPasswordIsBadRequest() throws Exception {
        login(EMAIL, "wrong-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password wrong."));
    }

    @Test
    void unknownEmailGivesSameErrorAsWrongPassword() throws Exception {
        login("nobody@mosbach.de", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password wrong."));
    }

    @Test
    void logoutInvalidatesTokenOnServer() throws Exception {
        String token = loginToken();

        logout(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User logged off."));
        assertThat(authTokenRepository.findAll()).isEmpty();

        logout(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token wrong."));
    }

    @Test
    void logoutWithUnknownTokenIsBadRequest() throws Exception {
        logout("does-not-exist")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token wrong."));
    }

    @Test
    void logoutWithoutTokenIsBadRequest() throws Exception {
        mockMvc.perform(delete("/auth").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token wrong."));
    }
}
