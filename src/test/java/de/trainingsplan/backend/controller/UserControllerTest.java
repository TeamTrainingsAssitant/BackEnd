package de.trainingsplan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.DeleteUserRequest;
import de.trainingsplan.backend.controller.dto.MessageResponse;
import de.trainingsplan.backend.controller.dto.RegisterRequest;
import de.trainingsplan.backend.controller.dto.RegisterResponse;
import de.trainingsplan.backend.controller.dto.UserExportResponse;
import de.trainingsplan.backend.controller.dto.UserResponse;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

class UserControllerTest {

    private UserService userService;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        controller = new UserController(userService);
    }

    private User userWithId(long id) {
        User user = new User("max", "max@mosbach.de", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void registerReturnsCreatedWithUserId() {
        RegisterRequest request = new RegisterRequest("max", "max@mosbach.de", "super-safe");
        when(userService.register(request)).thenReturn(userWithId(42L));

        ResponseEntity<RegisterResponse> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("User created.");
        assertThat(response.getBody().userId()).isEqualTo(42L);
    }

    @Test
    void registerPassesServiceErrorThrough() {
        RegisterRequest request = new RegisterRequest("max", "max@mosbach.de", "super-safe");
        when(userService.register(request))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "Email already registered."));

        assertThatThrownBy(() -> controller.register(request))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void meReturnsDataOfAuthenticatedUser() {
        UserResponse response = controller.me(userWithId(7L));

        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.username()).isEqualTo("max");
        assertThat(response.email()).isEqualTo("max@mosbach.de");
    }

    @Test
    void deleteReturnsMessageAndPassesPasswordToService() {
        User user = userWithId(7L);

        MessageResponse response = controller.delete(user, new DeleteUserRequest("super-safe"));

        assertThat(response.message()).isEqualTo("Account deleted.");
        verify(userService).deleteAccount(user, "super-safe");
    }

    @Test
    void deletePassesServiceErrorThrough() {
        User user = userWithId(7L);
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "Password wrong."))
                .when(userService).deleteAccount(user, "wrong");

        assertThatThrownBy(() -> controller.delete(user, new DeleteUserRequest("wrong")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Password wrong.");
                });
    }

    @Test
    void exportReturnsWhatTheServiceDelivers() {
        User user = userWithId(7L);
        UserExportResponse export = new UserExportResponse(UserResponse.from(user), List.of());
        when(userService.export(user)).thenReturn(export);

        assertThat(controller.export(user)).isSameAs(export);
    }
}
