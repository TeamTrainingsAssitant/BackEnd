package de.trainingsplan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.ExerciseDetailResponse;
import de.trainingsplan.backend.controller.dto.ExerciseListResponse;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.service.ExerciseCatalog;
import de.trainingsplan.backend.service.ExerciseCatalog.SearchResult;
import de.trainingsplan.backend.service.ExerciseDbClient.ExternalExercise;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExerciseDbControllerTest {

    private ExerciseCatalog catalog;
    private ExerciseDbController controller;
    private ExternalExercise bench;

    @BeforeEach
    void setUp() {
        catalog = mock(ExerciseCatalog.class);
        controller = new ExerciseDbController(catalog);
        bench = new ExternalExercise("EIeI8Vf", "barbell bench press", "gif", List.of("pectorals"),
                List.of("triceps"), List.of("chest"), List.of("barbell"), List.of("Step:1 ..."));
        when(catalog.isLoaded()).thenReturn(true);
    }

    @Test
    void listReturnsTotalAndExercises() {
        when(catalog.search("bench", List.of("pectorals"), 0, 20))
                .thenReturn(new SearchResult(12, List.of(bench)));

        ExerciseListResponse response = controller.list("bench", List.of("pectorals"), 0, 20);

        assertThat(response.total()).isEqualTo(12);
        assertThat(response.exercises()).hasSize(1);
        assertThat(response.exercises().get(0).externalId()).isEqualTo("EIeI8Vf");
        assertThat(response.exercises().get(0).primaryMuscleGroups()).containsExactly("pectorals");
    }

    @Test
    void listLimitsPageSizeTo50() {
        when(catalog.search(any(), any(), anyInt(), anyInt())).thenReturn(new SearchResult(0, List.of()));

        controller.list(null, null, 0, 1000);

        verify(catalog).search(null, null, 0, 50);
    }

    @Test
    void listPropagatesCatalogErrors() {
        ApiException unavailable = new ApiException(HttpStatus.BAD_GATEWAY, "ExerciseDB not reachable.");
        when(catalog.search("bench", null, 0, 20)).thenThrow(unavailable);

        assertThatThrownBy(() -> controller.list("bench", null, 0, 20)).isSameAs(unavailable);
    }

    @Test
    void getReturnsDetailsWithInstructions() {
        when(catalog.findById("EIeI8Vf")).thenReturn(bench);

        ExerciseDetailResponse response = controller.get("EIeI8Vf");

        assertThat(response.name()).isEqualTo("barbell bench press");
        assertThat(response.instructions()).containsExactly("Step:1 ...");
    }

    @Test
    void getUnknownExerciseIs404() {
        assertThatThrownBy(() -> controller.get("unknown"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Exercise not found.");
                });
    }

    @Test
    void musclesAndBodyPartsComeFromCatalog() {
        when(catalog.getMuscles()).thenReturn(List.of("pectorals", "triceps"));
        when(catalog.getBodyParts()).thenReturn(List.of("chest"));

        assertThat(controller.muscles()).containsExactly("pectorals", "triceps");
        assertThat(controller.bodyParts()).containsExactly("chest");
    }

    private void assertBadGateway(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(e.getMessage()).isEqualTo("ExerciseDB not reachable.");
        });
    }

    @Test
    void listIs502WhileCatalogIsNotLoaded() {
        when(catalog.isLoaded()).thenReturn(false);

        assertBadGateway(() -> controller.list(null, null, 0, 20));
    }

    @Test
    void getIs502WhileCatalogIsNotLoaded() {
        when(catalog.isLoaded()).thenReturn(false);

        assertBadGateway(() -> controller.get("EIeI8Vf"));
    }

    @Test
    void musclesIs502WhileCatalogIsNotLoaded() {
        when(catalog.isLoaded()).thenReturn(false);

        assertBadGateway(() -> controller.muscles());
    }

    @Test
    void bodyPartsIs502WhileCatalogIsNotLoaded() {
        when(catalog.isLoaded()).thenReturn(false);

        assertBadGateway(() -> controller.bodyParts());
    }
}
