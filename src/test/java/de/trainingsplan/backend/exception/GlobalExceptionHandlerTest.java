package de.trainingsplan.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;

import de.trainingsplan.backend.controller.dto.MessageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionKeepsStatusAndMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleApiException(new ApiException(HttpStatus.CONFLICT, "Email already registered."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new MessageResponse("Email already registered."));
    }

    @Test
    void invalidRequestIsBadRequest() {
        ResponseEntity<MessageResponse> response = handler.handleInvalidRequest(new RuntimeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void unexpectedErrorDoesNotLeakDetails() {
        ResponseEntity<MessageResponse> response =
                handler.handleUnexpected(new RuntimeException("secret detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(new MessageResponse("Internal server error."));
    }
}
