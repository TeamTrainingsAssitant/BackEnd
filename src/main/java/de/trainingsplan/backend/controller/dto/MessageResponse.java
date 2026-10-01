package de.trainingsplan.backend.controller.dto;

/**
 * Einheitliches Antwortformat für Fehler und einfache Bestätigungen: {@code { "message": "..." }}.
 */
public record MessageResponse(String message) {
}
