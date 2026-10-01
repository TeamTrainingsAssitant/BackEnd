package de.trainingsplan.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Fachlicher Fehler mit HTTP-Status und Message, wird vom {@link GlobalExceptionHandler}
 * in das einheitliche Fehlerformat übersetzt.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
