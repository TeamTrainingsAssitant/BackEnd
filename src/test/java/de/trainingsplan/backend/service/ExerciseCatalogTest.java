package de.trainingsplan.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.service.ExerciseDbClient.ExercisePage;
import de.trainingsplan.backend.service.ExerciseDbClient.ExternalExercise;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExerciseCatalogTest {

    private ExerciseDbClient client;
    private ExerciseCatalog catalog;

    @BeforeEach
    void setUp() {
        client = mock(ExerciseDbClient.class);
        // keine Wartezeiten im Test
        catalog = new ExerciseCatalog(client, 0, 0);
    }

    private static ExternalExercise exercise(String id, String name) {
        return new ExternalExercise(id, name, "gif", List.of("pectorals"), List.of(), List.of(), List.of(),
                List.of());
    }

    @Test
    void loadCollectsAllPages() {
        when(client.listExercises(null)).thenReturn(new ExercisePage(List.of(exercise("a", "A")), "cursor1"));
        when(client.listExercises("cursor1")).thenReturn(new ExercisePage(List.of(exercise("b", "B")), null));
        when(client.getMuscles()).thenReturn(List.of("pectorals"));
        when(client.getBodyParts()).thenReturn(List.of("chest"));

        catalog.load();

        assertTrue(catalog.isLoaded());
        assertEquals(2, catalog.getAll().size());
        assertEquals("B", catalog.findById("b").name());
        assertNull(catalog.findById("unknown"));
        assertEquals(List.of("pectorals"), catalog.getMuscles());
        assertEquals(List.of("chest"), catalog.getBodyParts());
    }

    @Test
    void loadRetriesAfterRateLimit() {
        ApiException rateLimit = new ApiException(HttpStatus.BAD_GATEWAY, "ExerciseDB not reachable.");
        when(client.listExercises(null))
                .thenThrow(rateLimit)
                .thenReturn(new ExercisePage(List.of(exercise("a", "A")), null));
        when(client.getMuscles()).thenReturn(List.of());
        when(client.getBodyParts()).thenReturn(List.of());

        catalog.load();

        assertEquals(1, catalog.getAll().size());
    }

    @Test
    void loadKeepsOldListWhenItFails() {
        when(client.listExercises(null)).thenReturn(new ExercisePage(List.of(exercise("a", "A")), null));
        when(client.getMuscles()).thenReturn(List.of());
        when(client.getBodyParts()).thenReturn(List.of());
        catalog.load();

        when(client.listExercises(null))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "ExerciseDB not reachable."));
        catalog.load();

        assertEquals(1, catalog.getAll().size());
    }

    @Test
    void isNotLoadedBeforeFirstLoad() {
        assertFalse(catalog.isLoaded());
    }
}
