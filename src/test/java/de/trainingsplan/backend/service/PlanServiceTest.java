package de.trainingsplan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.PlanExerciseRequest;
import de.trainingsplan.backend.controller.dto.PlanExerciseUpdateRequest;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.PlanExercise;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.PlanExerciseRepository;
import de.trainingsplan.backend.repository.PlanRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class PlanServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PLAN_ID = 5L;

    private PlanRepository planRepository;
    private PlanExerciseRepository planExerciseRepository;
    private ExerciseDbClient exerciseDbClient;
    private PlanService planService;
    private User user;
    private Plan plan;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        planExerciseRepository = mock(PlanExerciseRepository.class);
        exerciseDbClient = mock(ExerciseDbClient.class);
        planService = new PlanService(planRepository, planExerciseRepository, exerciseDbClient);

        user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        plan = new Plan(user, "Push Day", null);
        when(planRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));
        when(planExerciseRepository.save(any(PlanExercise.class))).thenAnswer(inv -> inv.getArgument(0));
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
}
