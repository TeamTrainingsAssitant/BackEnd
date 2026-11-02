package de.trainingsplan.backend.exception;

import de.trainingsplan.backend.controller.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /** Nennt das erste ungültige Feld, ohne den gesendeten Wert zu wiederholen. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MessageResponse> handleValidationError(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldError();
        String message = error == null ? "Invalid request." : error.getField() + " " + error.getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse(message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<MessageResponse> handleInvalidRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse("Invalid request."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponse> handleUnexpected(Exception ex) {
        // Von Spring selbst erzeugte Fehler (404 unbekannte URL, 405, 415 ...) behalten ihren Status.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            String message = clientErrorMessage(errorResponse.getStatusCode());
            return ResponseEntity.status(errorResponse.getStatusCode()).body(new MessageResponse(message));
        }
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponse("Internal server error."));
    }

    private String clientErrorMessage(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return "Not found.";
        }
        if (status.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return "Invalid request.";
        }
        return "Request not supported.";
    }
}
