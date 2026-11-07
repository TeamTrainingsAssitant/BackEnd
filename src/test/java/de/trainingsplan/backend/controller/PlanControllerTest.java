package de.trainingsplan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.MessageResponse;
import de.trainingsplan.backend.controller.dto.PlanExerciseReplacementRequest;
import de.trainingsplan.backend.controller.dto.PlanExerciseOrderRequest;
import de.trainingsplan.backend.controller.dto.PlanExerciseRequest;
import de.trainingsplan.backend.controller.dto.PlanExerciseResponse;
import de.trainingsplan.backend.controller.dto.PlanExerciseUpdateRequest;
import de.trainingsplan.backend.controller.dto.PlanListItemResponse;
import de.trainingsplan.backend.controller.dto.PlanRequest;
import de.trainingsplan.backend.controller.dto.PlanResponse;
import de.trainingsplan.backend.controller.dto.TrainingSetRequest;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.PlanExercise;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.service.PlanService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

class PlanControllerTest {

    private PlanService planService;
    private PlanController controller;
    private User user;

    @BeforeEach
    void setUp() {
        planService = mock(PlanService.class);
        controller = new PlanController(planService);
        user = new User("max", "max@mosbach.de", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private Plan planWithId(long id, String name) {
        Plan plan = new Plan(user, name);
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    private PlanExercise exerciseWithId(Plan plan, long id, int position) {
        PlanExercise exercise = new PlanExercise(plan, "bench-press", "Bankdrücken", "gif",
                List.of("pectorals"), List.of("triceps"), position, "Notiz");
        ReflectionTestUtils.setField(exercise, "id", id);
        exercise.updateTrainingSets(List.of(new de.trainingsplan.backend.model.TrainingSet(80.0, 10)));
        return exercise;
    }

    @Test
    void listMapsPlansToListItemsWithExerciseCounts() {
        when(planService.list(user)).thenReturn(List.of(planWithId(10L, "Push"), planWithId(11L, "Pull")));
        when(planService.exerciseCount(10L)).thenReturn(3);
        when(planService.exerciseCount(11L)).thenReturn(1);

        List<PlanListItemResponse> response = controller.list(user);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(PlanListItemResponse::id).containsExactly(10L, 11L);
        assertThat(response).extracting(PlanListItemResponse::name).containsExactly("Push", "Pull");
        assertThat(response).extracting(PlanListItemResponse::exerciseCount).containsExactly(3, 1);
    }

    @Test
    void listOfUserWithoutPlansIsEmpty() {
        when(planService.list(user)).thenReturn(List.of());

        assertThat(controller.list(user)).isEmpty();
    }

    @Test
    void createReturnsCreatedWithPlan() {
        PlanRequest request = new PlanRequest("Push");
        when(planService.create(user, request)).thenReturn(planWithId(10L, "Push"));

        ResponseEntity<PlanResponse> response = controller.create(user, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(10L);
        assertThat(response.getBody().name()).isEqualTo("Push");
    }

    @Test
    void getReturnsPlanOfService() {
        when(planService.get(user, 10L)).thenReturn(planWithId(10L, "Push"));

        PlanResponse response = controller.get(user, 10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Push");
    }

    @Test
    void updateReturnsUpdatedPlan() {
        PlanRequest request = new PlanRequest("Neu");
        when(planService.update(user, 10L, request)).thenReturn(planWithId(10L, "Neu"));

        PlanResponse response = controller.update(user, 10L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Neu");
    }

    @Test
    void deleteCallsServiceAndReturnsMessage() {
        MessageResponse response = controller.delete(user, 10L);

        verify(planService).delete(user, 10L);
        assertThat(response.message()).isEqualTo("Plan deleted.");
    }

    @Test
    void listExercisesMapsExercisesAndTrainingValues() {
        Plan plan = planWithId(10L, "Push Day");
        PlanExercise exercise = exerciseWithId(plan, 7L, 1);
        when(planService.listExercises(user, 10L)).thenReturn(List.of(exercise));

        List<PlanExerciseResponse> response = controller.listExercises(user, 10L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(7L);
        assertThat(response.get(0).name()).isEqualTo("Bankdrücken");
        assertThat(response.get(0).weight()).isEqualTo(80.0);
        assertThat(response.get(0).sets()).isEqualTo(1);
        assertThat(response.get(0).reps()).isEqualTo(10);
        assertThat(response.get(0).trainingSets()).hasSize(1);
        verify(planService).listExercises(user, 10L);
    }

    @Test
    void addExerciseReturnsCreatedAndMappedExercise() {
        Plan plan = planWithId(10L, "Push Day");
        PlanExercise exercise = exerciseWithId(plan, 7L, 1);
        PlanExerciseRequest request = new PlanExerciseRequest("bench-press", "Notiz", 80.0, 1, 10,
                List.of(new TrainingSetRequest(80.0, 10)));
        when(planService.addExercise(user, 10L, request)).thenReturn(exercise);

        ResponseEntity<PlanExerciseResponse> response = controller.addExercise(user, 10L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(7L);
        assertThat(response.getBody().weight()).isEqualTo(80.0);
        verify(planService).addExercise(user, 10L, request);
    }

    @Test
    void updateExerciseMapsUpdatedExercise() {
        Plan plan = planWithId(10L, "Push Day");
        PlanExercise exercise = exerciseWithId(plan, 7L, 1);
        PlanExerciseUpdateRequest request = new PlanExerciseUpdateRequest("Neue Notiz", 90.0, 1, 8);
        when(planService.updateExercise(user, 10L, 7L, request)).thenReturn(exercise);

        PlanExerciseResponse response = controller.updateExercise(user, 10L, 7L, request);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.note()).isEqualTo("Notiz");
        assertThat(response.trainingSets()).extracting("weight").containsExactly(80.0);
        verify(planService).updateExercise(user, 10L, 7L, request);
    }

    @Test
    void deleteExerciseCallsServiceAndReturnsMessage() {
        MessageResponse response = controller.deleteExercise(user, 10L, 7L);

        verify(planService).deleteExercise(user, 10L, 7L);
        assertThat(response.message()).isEqualTo("Exercise removed.");
    }

    @Test
    void alternativesMapsExercisesFromService() {
        Plan plan = planWithId(10L, "Push Day");
        PlanExercise exercise = exerciseWithId(plan, 8L, 2);
        when(planService.alternatives(user, 10L, 7L, 5)).thenReturn(List.of(
                new de.trainingsplan.backend.service.ExerciseDbClient.ExternalExercise(
                        "incline-press", "Schrägbankdrücken", "gif", List.of("pectorals"),
                        List.of("triceps"), List.of("chest"), List.of("barbell"), List.of())));

        var response = controller.alternatives(user, 10L, 7L, 5);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).externalId()).isEqualTo("incline-press");
        verify(planService).alternatives(user, 10L, 7L, 5);
    }

    @Test
    void replaceExerciseMapsReplacement() {
        Plan plan = planWithId(10L, "Push Day");
        PlanExercise exercise = exerciseWithId(plan, 7L, 1);
        PlanExerciseReplacementRequest request = new PlanExerciseReplacementRequest("incline-press");
        when(planService.replaceExercise(user, 10L, 7L, "incline-press")).thenReturn(exercise);

        PlanExerciseResponse response = controller.replaceExercise(user, 10L, 7L, request);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.externalId()).isEqualTo("bench-press");
        verify(planService).replaceExercise(user, 10L, 7L, "incline-press");
    }

    @Test
    void exerciseEndpointErrorsArePassedThrough() {
        ApiException notFound = new ApiException(HttpStatus.NOT_FOUND, "Exercise not found.");
        when(planService.listExercises(user, 99L)).thenThrow(notFound);

        assertThatThrownBy(() -> controller.listExercises(user, 99L)).isSameAs(notFound);
    }

    @Test
    void addExerciseErrorIsPassedThrough() {
        ApiException conflict = new ApiException(HttpStatus.CONFLICT, "Exercise already exists.");
        PlanExerciseRequest request = new PlanExerciseRequest("bench-press", null, null, null, null);
        when(planService.addExercise(user, 10L, request)).thenThrow(conflict);

        assertThatThrownBy(() -> controller.addExercise(user, 10L, request)).isSameAs(conflict);
    }

    @Test
    void updateExerciseErrorIsPassedThrough() {
        ApiException badRequest = new ApiException(HttpStatus.BAD_REQUEST, "Invalid request.");
        PlanExerciseUpdateRequest request = new PlanExerciseUpdateRequest(null, null, null, null);
        when(planService.updateExercise(user, 10L, 7L, request)).thenThrow(badRequest);

        assertThatThrownBy(() -> controller.updateExercise(user, 10L, 7L, request)).isSameAs(badRequest);
    }

    @Test
    void deleteExerciseErrorIsPassedThrough() {
        ApiException notFound = new ApiException(HttpStatus.NOT_FOUND, "Exercise not found.");
        org.mockito.Mockito.doThrow(notFound).when(planService).deleteExercise(user, 10L, 7L);

        assertThatThrownBy(() -> controller.deleteExercise(user, 10L, 7L)).isSameAs(notFound);
    }

    @Test
    void alternativesErrorIsPassedThrough() {
        ApiException notFound = new ApiException(HttpStatus.NOT_FOUND, "Exercise not found.");
        when(planService.alternatives(user, 10L, 7L, 5)).thenThrow(notFound);

        assertThatThrownBy(() -> controller.alternatives(user, 10L, 7L, 5)).isSameAs(notFound);
    }

    @Test
    void replaceExerciseErrorIsPassedThrough() {
        ApiException notFound = new ApiException(HttpStatus.NOT_FOUND, "Exercise not found.");
        PlanExerciseReplacementRequest request = new PlanExerciseReplacementRequest("incline-press");
        when(planService.replaceExercise(user, 10L, 7L, "incline-press")).thenThrow(notFound);

        assertThatThrownBy(() -> controller.replaceExercise(user, 10L, 7L, request)).isSameAs(notFound);
    }

    @Test
    void reorderExercisesReturnsExercisesInNewOrder() {
        Plan plan = planWithId(10L, "Push Day");
        PlanExercise first = new PlanExercise(plan, "a", "Rudern", null, List.of(), List.of(), 1, null);
        PlanExercise second = new PlanExercise(plan, "b", "Bankdrücken", null, List.of(), List.of(), 2, null);
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(planService.reorderExercises(user, 10L, List.of(2L, 1L))).thenReturn(List.of(second, first));

        List<PlanExerciseResponse> response =
                controller.reorderExercises(user, 10L, new PlanExerciseOrderRequest(List.of(2L, 1L)));

        assertThat(response).extracting(PlanExerciseResponse::id).containsExactly(2L, 1L);
    }

    @Test
    void notFoundFromServiceIsPassedThrough() {
        when(planService.get(user, 99L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));

        assertThatThrownBy(() -> controller.get(user, 99L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Plan not found.");
                });
    }
}
