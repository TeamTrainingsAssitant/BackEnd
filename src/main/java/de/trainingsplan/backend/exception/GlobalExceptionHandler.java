package de.trainingsplan.backend.exception;

import de.trainingsplan.backend.controller.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Übersetzt alle Fehler in das Format {@code { "message": "..." }}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<MessageResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<MessageResponse> handleInvalidRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse("Invalid request."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponse> handleUnexpected(Exception ex) {
        // Von Spring selbst erzeugte Fehler (404 unbekannte URL, 405, 415 ...) behalten ihren Status.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            String message = errorResponse.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                    ? "Not found."
                    : "Request not supported.";
            return ResponseEntity.status(errorResponse.getStatusCode()).body(new MessageResponse(message));
        }
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponse("Internal server error."));
    }
}
