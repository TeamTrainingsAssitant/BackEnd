package de.trainingsplan.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.trainingsplan.backend.model.AuthToken;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.AuthTokenRepository;
import de.trainingsplan.backend.repository.UserRepository;
import de.trainingsplan.backend.service.TokenService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TokenAuthenticationTest {

    private static final String NOT_AUTHENTICATED = "{\"message\":\"Not authenticated.\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private TokenService tokenService;

    private User user;

    @BeforeEach
    void setUp() {
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(new User("max", "max@mosbach.de", "hash"));
    }

    @Test
    void noAuthorizationHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(NOT_AUTHENTICATED));
    }

    @Test
    void unknownTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(NOT_AUTHENTICATED));
    }

    @Test
    void otherAuthSchemeIsUnauthorized() throws Exception {
        String token = tokenService.createToken(user);

        mockMvc.perform(get("/users/me").header("Authorization", "Basic " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        authTokenRepository.save(new AuthToken(TokenService.hash("expired"), user,
                Instant.now().minusSeconds(1)));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReturnsOwnProfile() throws Exception {
        String token = tokenService.createToken(user);

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.username").value("max"))
                .andExpect(jsonPath("$.email").value("max@mosbach.de"))
                .andExpect(jsonPath("$.createdAt").value(user.getCreatedAt().toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void tokenIsUnusableAfterLogout() throws Exception {
        String token = tokenService.createToken(user);
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        mockMvc.perform(post("/auth").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@y.de\",\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPathWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(NOT_AUTHENTICATED));
    }

    @Test
    void unknownPathWithTokenIsNotFoundInErrorFormat() throws Exception {
        String token = tokenService.createToken(user);

        mockMvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found."));
    }
}
