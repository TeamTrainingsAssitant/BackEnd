package de.trainingsplan.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.trainingsplan.backend.controller.dto.MessageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @RestController
    static class IdController {

        @GetMapping("/items/{id}")
        String get(@PathVariable Long id) {
            return "ok";
        }
    }

    @Test
    void wrongParameterTypeIsBadRequest() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IdController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(get("/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request."));
    }

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
    void springClientErrorsKeepTheirStatus() {
        ResponseEntity<MessageResponse> notFound =
                handler.handleUnexpected(new ErrorResponseException(HttpStatus.NOT_FOUND));
        ResponseEntity<MessageResponse> notAllowed =
                handler.handleUnexpected(new ErrorResponseException(HttpStatus.METHOD_NOT_ALLOWED));

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).isEqualTo(new MessageResponse("Not found."));
        assertThat(notAllowed.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(notAllowed.getBody()).isEqualTo(new MessageResponse("Request not supported."));
    }

    @Test
    void unexpectedErrorDoesNotLeakDetails() {
        ResponseEntity<MessageResponse> response =
                handler.handleUnexpected(new RuntimeException("secret detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(new MessageResponse("Internal server error."));
    }

    @SuppressWarnings("unused")
    private void dummyControllerMethod(String body) {
    }

    @Test
    void validationErrorIsBadRequestWithGenericMessage() throws Exception {
        MethodParameter parameter =
                new MethodParameter(getClass().getDeclaredMethod("dummyControllerMethod", String.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "planRequest");
        bindingResult.rejectValue(null, "NotBlank", "name must not be blank");

        ResponseEntity<MessageResponse> response =
                handler.handleInvalidRequest(new MethodArgumentNotValidException(parameter, bindingResult));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new MessageResponse("Invalid request."));
    }

    @Test
    void unreadableBodyIsBadRequestWithGenericMessage() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("JSON parse error: secret detail", mock(HttpInputMessage.class));

        ResponseEntity<MessageResponse> response = handler.handleInvalidRequest(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new MessageResponse("Invalid request."));
    }

    @Test
    void apiExceptionWithNotFoundKeepsItsOwnMessage() {
        ResponseEntity<MessageResponse> response =
                handler.handleApiException(new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new MessageResponse("Plan not found."));
    }

    @Test
    void otherSpringClientErrorsAreReportedAsNotSupported() {
        ResponseEntity<MessageResponse> unsupported =
                handler.handleUnexpected(new ErrorResponseException(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        ResponseEntity<MessageResponse> badRequest =
                handler.handleUnexpected(new ErrorResponseException(HttpStatus.BAD_REQUEST));

        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(unsupported.getBody()).isEqualTo(new MessageResponse("Request not supported."));
        assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badRequest.getBody()).isEqualTo(new MessageResponse("Request not supported."));
    }

    @Test
    void springServerErrorsBecomeGenericInternalServerError() {
        ResponseEntity<MessageResponse> response =
                handler.handleUnexpected(new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(new MessageResponse("Internal server error."));
    }

    @Test
    void exceptionWithoutMessageIsStillInternalServerError() {
        ResponseEntity<MessageResponse> response = handler.handleUnexpected(new NullPointerException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(new MessageResponse("Internal server error."));
    }
}
