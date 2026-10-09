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
    private PlanService planService;
    private User user;
    private User otherUser;
    private Plan plan;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        planExerciseRepository = mock(PlanExerciseRepository.class);
        exerciseDbClient = mock(ExerciseDbClient.class);
        planService = new PlanService(planRepository, planExerciseRepository, exerciseDbClient);

        user = userWithId("max", USER_ID);
        otherUser = userWithId("anna", 2L);
        plan = new Plan(user, "Push Day", null);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(planExerciseRepository.save(any(PlanExercise.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User userWithId(String name, long id) {
        User u = new User(name, name + "@mosbach.de", "hash");
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Plan planWithId(User owner, long id) {
        Plan plan = new Plan(owner, "Push Day", "Brust und Schulter");
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
    void addExerciseAppendsAtEndWithDataFromExerciseDb() {
        when(exerciseDbClient.getExercise("01qpYSe")).thenReturn(new ExerciseDbClient.ExternalExercise(
                "01qpYSe", "upward facing dog", "https://static.exercisedb.dev/media/01qpYSe.gif",
                List.of("erector spinae"), List.of("deltoids", "pectorals")));
        when(planExerciseRepository.countByPlanId(PLAN_ID)).thenReturn(2);

        planService.addExercise(user, PLAN_ID, new PlanExerciseRequest(" 01qpYSe ", "Sitz 4"));

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

        assertThatThrownBy(() -> planService.addExercise(user, 99L, new PlanExerciseRequest("01qpYSe", null)))
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

        planService.updateExercise(user, PLAN_ID, 7L, new PlanExerciseUpdateRequest("Sitz 4"));
        assertThat(exercise.getNote()).isEqualTo("Sitz 4");

        planService.updateExercise(user, PLAN_ID, 7L, new PlanExerciseUpdateRequest(null));
        assertThat(exercise.getNote()).isNull();
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

        Plan created = planService.create(user, new PlanRequest("  Pull Day  ", "Rücken"));

        ArgumentCaptor<Plan> saved = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Pull Day");
        assertThat(saved.getValue().getDescription()).isEqualTo("Rücken");
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(created).isSameAs(saved.getValue());
    }

    @Test
    void createAllowsMissingDescription() {
        when(planRepository.save(any(Plan.class))).thenAnswer(call -> call.getArgument(0));

        Plan created = planService.create(user, new PlanRequest("Leg Day", null));

        assertThat(created.getDescription()).isNull();
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
    void updateChangesNameAndDescriptionAndTouchesUpdatedAt() {
        Plan plan = planWithId(user, 10L);
        LocalDateTime createdAt = plan.getCreatedAt();
        when(planRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(plan));

        Plan updated = planService.update(user, 10L, new PlanRequest("  Neuer Name ", "Neu"));

        assertThat(updated).isSameAs(plan);
        assertThat(updated.getName()).isEqualTo("Neuer Name");
        assertThat(updated.getDescription()).isEqualTo("Neu");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void updateForeignOrUnknownPlanIsNotFound() {
        assertNotFound(() -> planService.update(user, 99L, new PlanRequest("Name", null)));
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
