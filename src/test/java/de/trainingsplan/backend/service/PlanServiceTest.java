package de.trainingsplan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.PlanExerciseRequest;
import de.trainingsplan.backend.controller.dto.PlanExerciseUpdateRequest;
import de.trainingsplan.backend.controller.dto.PlanRequest;
import de.trainingsplan.backend.controller.dto.TrainingSetRequest;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.PlanExercise;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.PlanExerciseRepository;
import de.trainingsplan.backend.repository.PlanRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class PlanServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PLAN_ID = 5L;

    private PlanRepository planRepository;
    private PlanExerciseRepository planExerciseRepository;
    private ExerciseDbClient exerciseDbClient;
    private ExerciseCatalog exerciseCatalog;
    private PlanService planService;
    private User user;
    private User otherUser;
    private Plan plan;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        planExerciseRepository = mock(PlanExerciseRepository.class);
        exerciseDbClient = mock(ExerciseDbClient.class);
        exerciseCatalog = mock(ExerciseCatalog.class);
        planService = new PlanService(planRepository, planExerciseRepository, exerciseDbClient, exerciseCatalog);

        user = userWithId("max", USER_ID);
        otherUser = userWithId("anna", 2L);
        plan = new Plan(user, "Push Day");
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(planExerciseRepository.save(any(PlanExercise.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User userWithId(String name, long id) {
        User u = new User(name, name + "@mosbach.de", "hash");
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Plan planWithId(User owner, long id) {
        Plan plan = new Plan(owner, "Push Day");
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getMessage()).isEqualTo("Plan not found.");
        });
    }

    @Test
    void addExerciseUsesCatalogWithoutCallingExerciseDb() {
        when(exerciseCatalog.findById("01qpYSe")).thenReturn(new ExerciseDbClient.ExternalExercise(
                "01qpYSe", "upward facing dog", "https://static.exercisedb.dev/media/01qpYSe.gif",
                List.of("erector spinae"), List.of("deltoids"), List.of("back"), List.of(), List.of()));
        when(planExerciseRepository.countByPlanId(PLAN_ID)).thenReturn(0);

        PlanExercise result = planService.addExercise(user, PLAN_ID, new PlanExerciseRequest("01qpYSe", null, null, null, null));

        assertThat(result.getName()).isEqualTo("upward facing dog");
        assertThat(result.getPrimaryMuscleGroups()).containsExactly("erector spinae");
        verify(exerciseDbClient, never()).getExercise(any());
    }

    @Test
    void addExerciseAppendsAtEndWithDataFromExerciseDb() {
        when(exerciseDbClient.getExercise("01qpYSe")).thenReturn(new ExerciseDbClient.ExternalExercise(
                "01qpYSe", "upward facing dog", "https://static.exercisedb.dev/media/01qpYSe.gif",
                List.of("erector spinae"), List.of("deltoids", "pectorals"),
                List.of("back"), List.of("bodyweight"), List.of()));
        when(planExerciseRepository.countByPlanId(PLAN_ID)).thenReturn(2);

        planService.addExercise(user, PLAN_ID, new PlanExerciseRequest(" 01qpYSe ", "Sitz 4", null, null, null));

        ArgumentCaptor<PlanExercise> saved = ArgumentCaptor.forClass(PlanExercise.class);
        verify(planExerciseRepository).save(saved.capture());
        PlanExercise exercise = saved.getValue();
        assertThat(exercise.getExternalId()).isEqualTo("01qpYSe");
        assertThat(exercise.getName()).isEqualTo("upward facing dog");
        assertThat(exercise.getPrimaryMuscleGroups()).containsExactly("erector spinae");
        assertThat(exercise.getSecondaryMuscleGroups()).containsExactly("deltoids", "pectorals");
        assertThat(exercise.getPosition()).isEqualTo(3);
        assertThat(exercise.getNote()).isEqualTo("Sitz 4");
    }

    @Test
    void addExerciseToForeignPlanIsNotFound() {
        when(planRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planService.addExercise(user, 99L, new PlanExerciseRequest("01qpYSe", null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Plan not found.");
                });
        verifyNoInteractions(exerciseDbClient);
        verify(planExerciseRepository, never()).save(any());
    }

    @Test
    void updateExerciseChangesAndRemovesNote() {
        PlanExercise exercise = new PlanExercise(plan, "01qpYSe", "upward facing dog", null,
                List.of(), List.of(), 1, "alte Note");
        when(planExerciseRepository.findByIdAndPlanId(7L, PLAN_ID)).thenReturn(Optional.of(exercise));

        planService.updateExercise(user, PLAN_ID, 7L, new PlanExerciseUpdateRequest("Sitz 4", null, null, null));
        assertThat(exercise.getNote()).isEqualTo("Sitz 4");

        planService.updateExercise(user, PLAN_ID, 7L, new PlanExerciseUpdateRequest(null, null, null, null));
        assertThat(exercise.getNote()).isNull();
    }

    @Test
    void addExerciseSavesWeightSetsAndReps() {
        when(exerciseCatalog.findById("01qpYSe")).thenReturn(new ExerciseDbClient.ExternalExercise(
                "01qpYSe", "upward facing dog", null, List.of(), List.of(), List.of(), List.of(), List.of()));
        when(planExerciseRepository.countByPlanId(PLAN_ID)).thenReturn(0);

        planService.addExercise(user, PLAN_ID, new PlanExerciseRequest("01qpYSe", null, 82.5, 4, 8));

        ArgumentCaptor<PlanExercise> saved = ArgumentCaptor.forClass(PlanExercise.class);
        verify(planExerciseRepository).save(saved.capture());
        assertThat(saved.getValue().getWeight()).isEqualTo(82.5);
        assertThat(saved.getValue().getSets()).isEqualTo(4);
        assertThat(saved.getValue().getReps()).isEqualTo(8);
    }

    @Test
    void addExerciseAcceptsIndividualTrainingSets() {
        when(exerciseCatalog.findById("01qpYSe")).thenReturn(new ExerciseDbClient.ExternalExercise(
                "01qpYSe", "upward facing dog", null, List.of(), List.of(), List.of(), List.of(), List.of()));
        when(planExerciseRepository.countByPlanId(PLAN_ID)).thenReturn(0);

        planService.addExercise(user, PLAN_ID, new PlanExerciseRequest("01qpYSe", null, null, null, null,
                List.of(new TrainingSetRequest(80.0, 10),
                        new TrainingSetRequest(70.0, 8))));

        ArgumentCaptor<PlanExercise> saved = ArgumentCaptor.forClass(PlanExercise.class);
        verify(planExerciseRepository).save(saved.capture());
        assertThat(saved.getValue().getTrainingSets()).hasSize(2);
        assertThat(saved.getValue().getTrainingSets()).extracting("weight").containsExactly(80.0, 70.0);
    }

    @Test
    void updateExerciseChangesTrainingValuesAndPreservesThemWhenOmitted() {
        PlanExercise exercise = new PlanExercise(plan, "01qpYSe", "upward facing dog", null,
                List.of(), List.of(), 1, null);
        when(planExerciseRepository.findByIdAndPlanId(7L, PLAN_ID)).thenReturn(Optional.of(exercise));

        planService.updateExercise(user, PLAN_ID, 7L, new PlanExerciseUpdateRequest(null, 60.0, 3, 10));
        assertThat(exercise.getWeight()).isEqualTo(60.0);
        assertThat(exercise.getSets()).isEqualTo(3);
        assertThat(exercise.getReps()).isEqualTo(10);

        planService.updateExercise(user, PLAN_ID, 7L, new PlanExerciseUpdateRequest("new note", null, null, null));
        assertThat(exercise.getWeight()).isEqualTo(60.0);
        assertThat(exercise.getSets()).isEqualTo(3);
        assertThat(exercise.getReps()).isEqualTo(10);
    }

    @Test
    void updateExerciseOverwritesAllThreeValuesAtOnce() {
        PlanExercise exercise = new PlanExercise(plan, "01qpYSe", "upward facing dog", null,
                List.of(), List.of(), 1, null);
        exercise.updateTraining(60.0, 3, 10);
        when(planExerciseRepository.findByIdAndPlanId(7L, PLAN_ID)).thenReturn(Optional.of(exercise));

        planService.updateExercise(user, PLAN_ID, 7L, new PlanExerciseUpdateRequest(null, 70.0, null, null));

        assertThat(exercise.getWeight()).isEqualTo(70.0);
        assertThat(exercise.getSets()).isEqualTo(1);
        assertThat(exercise.getReps()).isNull();
    }

    @Test
    void updateExerciseWithEmptyTrainingSetsClearsTrainingValues() {
        PlanExercise exercise = new PlanExercise(plan, "01qpYSe", "upward facing dog", null,
                List.of(), List.of(), 1, null);
        exercise.updateTraining(60.0, 3, 10);
        when(planExerciseRepository.findByIdAndPlanId(7L, PLAN_ID)).thenReturn(Optional.of(exercise));

        planService.updateExercise(user, PLAN_ID, 7L,
                new PlanExerciseUpdateRequest(null, null, null, null, List.of()));

        assertThat(exercise.getTrainingSets()).isEmpty();
        assertThat(exercise.getWeight()).isNull();
        assertThat(exercise.getSets()).isNull();
        assertThat(exercise.getReps()).isNull();
    }

    @Test
    void replaceExerciseKeepsWeightSetsAndReps() {
        PlanExercise exercise = new PlanExercise(plan, "01qpYSe", "upward facing dog", null,
                List.of(), List.of(), 3, "Sitz 4");
        ReflectionTestUtils.setField(exercise, "id", 7L);
        exercise.updateTraining(60.0, 3, 10);
        when(planExerciseRepository.findByIdAndPlanId(7L, PLAN_ID)).thenReturn(Optional.of(exercise));
        when(planExerciseRepository.findByPlanIdOrderByPositionAsc(PLAN_ID)).thenReturn(List.of(exercise));
        when(exerciseCatalog.findById("dips01")).thenReturn(new ExerciseDbClient.ExternalExercise(
                "dips01", "dips", null, List.of("triceps"), List.of("pectorals"), List.of(), List.of(), List.of()));

        PlanExercise result = planService.replaceExercise(user, PLAN_ID, 7L, "dips01");

        assertThat(result.getExternalId()).isEqualTo("dips01");
        assertThat(result.getName()).isEqualTo("dips");
        assertThat(result.getWeight()).isEqualTo(60.0);
        assertThat(result.getSets()).isEqualTo(3);
        assertThat(result.getReps()).isEqualTo(10);
        assertThat(result.getNote()).isEqualTo("Sitz 4");
        assertThat(result.getPosition()).isEqualTo(3);
    }

    @Test
    void deleteExerciseRemovesItAndMovesFollowingExercisesUp() {
        PlanExercise second = new PlanExercise(plan, "a", "Kniebeugen", null, List.of(), List.of(), 2, null);
        PlanExercise third = new PlanExercise(plan, "b", "Rudern", null, List.of(), List.of(), 3, null);
        PlanExercise fourth = new PlanExercise(plan, "c", "Bizeps-Curls", null, List.of(), List.of(), 4, null);
        when(planExerciseRepository.findByIdAndPlanId(7L, PLAN_ID)).thenReturn(Optional.of(third));
        when(planExerciseRepository.findByPlanIdAndPositionGreaterThan(PLAN_ID, 3)).thenReturn(List.of(fourth));

        planService.deleteExercise(user, PLAN_ID, 7L);

        verify(planExerciseRepository).delete(third);
        assertThat(second.getPosition()).isEqualTo(2);
        assertThat(fourth.getPosition()).isEqualTo(3);
    }

    private PlanExercise exerciseWithId(long id, String name, int position) {
        PlanExercise exercise = new PlanExercise(plan, "ext" + id, name, null, List.of(), List.of(), position, null);
        ReflectionTestUtils.setField(exercise, "id", id);
        return exercise;
    }

    @Test
    void reorderExercisesAssignsPositionsInRequestedOrder() {
        PlanExercise first = exerciseWithId(1L, "Bankdrücken", 1);
        PlanExercise second = exerciseWithId(2L, "Kniebeugen", 2);
        PlanExercise third = exerciseWithId(3L, "Rudern", 3);
        when(planExerciseRepository.findByPlanIdOrderByPositionAsc(PLAN_ID))
                .thenReturn(List.of(first, second, third));
        LocalDateTime before = plan.getUpdatedAt();

        List<PlanExercise> result = planService.reorderExercises(user, PLAN_ID, List.of(3L, 1L, 2L));

        assertThat(result).containsExactly(third, first, second);
        assertThat(third.getPosition()).isEqualTo(1);
        assertThat(first.getPosition()).isEqualTo(2);
        assertThat(second.getPosition()).isEqualTo(3);
        assertThat(plan.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void reorderExercisesRejectsDuplicateIds() {
        when(planExerciseRepository.findByPlanIdOrderByPositionAsc(PLAN_ID))
                .thenReturn(List.of(exerciseWithId(1L, "A", 1), exerciseWithId(2L, "B", 2)));

        assertThatThrownBy(() -> planService.reorderExercises(user, PLAN_ID, List.of(1L, 1L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void reorderExercisesRejectsMissingOrForeignIds() {
        PlanExercise first = exerciseWithId(1L, "A", 1);
        PlanExercise second = exerciseWithId(2L, "B", 2);
        when(planExerciseRepository.findByPlanIdOrderByPositionAsc(PLAN_ID)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> planService.reorderExercises(user, PLAN_ID, List.of(1L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> planService.reorderExercises(user, PLAN_ID, List.of(1L, 99L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(second.getPosition()).isEqualTo(2);
    }

    @Test
    void reorderExercisesOfForeignPlanIsNotFound() {
        assertThatThrownBy(() -> planService.reorderExercises(otherUser, PLAN_ID, List.of(1L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(planExerciseRepository);
    }

    @Test
    void listAsksRepositoryForPlansOfThatUser() {
        List<Plan> plans = List.of(planWithId(user, 10L), planWithId(user, 11L));
        when(planRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(plans);

        assertThat(planService.list(user)).isSameAs(plans);
        verify(planRepository, never()).findByUserIdOrderByUpdatedAtDesc(2L);
    }

    @Test
    void createTrimsNameAndSavesPlanForUser() {
        when(planRepository.save(any(Plan.class))).thenAnswer(call -> call.getArgument(0));

        Plan created = planService.create(user, new PlanRequest("  Pull Day  "));

        ArgumentCaptor<Plan> saved = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Pull Day");
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(created).isSameAs(saved.getValue());
    }


    @Test
    void getReturnsOwnPlan() {
        Plan plan = planWithId(user, 10L);
        when(planRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(plan));

        assertThat(planService.get(user, 10L)).isSameAs(plan);
    }

    @Test
    void getUnknownPlanIsNotFound() {
        assertNotFound(() -> planService.get(user, 99L));
    }

    @Test
    void getForeignPlanLooksLikeUnknownPlan() {
        when(planRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(planWithId(user, 10L)));

        // Anna fragt Maxens Plan 10 ab: das Repository kennt (10, 2) nicht
        assertNotFound(() -> planService.get(otherUser, 10L));
        verify(planRepository).findByIdAndUserId(10L, 2L);
    }

    @Test
    void updateChangesNameAndTouchesUpdatedAt() {
        Plan plan = planWithId(user, 10L);
        LocalDateTime createdAt = plan.getCreatedAt();
        when(planRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(plan));

        Plan updated = planService.update(user, 10L, new PlanRequest("  Neuer Name "));

        assertThat(updated).isSameAs(plan);
        assertThat(updated.getName()).isEqualTo("Neuer Name");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void updateForeignOrUnknownPlanIsNotFound() {
        assertNotFound(() -> planService.update(user, 99L, new PlanRequest("Name")));
    }

    @Test
    void deleteRemovesOwnPlan() {
        Plan plan = planWithId(user, 10L);
        when(planRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(plan));

        planService.delete(user, 10L);

        verify(planRepository).delete(plan);
    }

    @Test
    void deleteForeignOrUnknownPlanIsNotFoundAndDeletesNothing() {
        assertNotFound(() -> planService.delete(user, 99L));
        verify(planRepository, never()).delete(any(Plan.class));
    }
}
