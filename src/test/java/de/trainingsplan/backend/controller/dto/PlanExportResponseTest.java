package de.trainingsplan.backend.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.PlanExercise;
import de.trainingsplan.backend.model.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlanExportResponseTest {

    private Plan plan() {
        Plan plan = new Plan(new User("max", "max@mosbach.de", "hash"), "Push");
        ReflectionTestUtils.setField(plan, "id", 10L);
        return plan;
    }

    private PlanExercise exercise(Plan plan, long id, String externalId, String name, int position, String note) {
        PlanExercise exercise = new PlanExercise(plan, externalId, name, "http://gif/" + externalId,
                List.of("pectorals"), List.of("triceps"), position, note);
        ReflectionTestUtils.setField(exercise, "id", id);
        return exercise;
    }

    @Test
    void fromMapsPlanFieldsAndExercisesInGivenOrder() {
        Plan plan = plan();
        PlanExercise first = exercise(plan, 1L, "ex1", "Bench Press", 0, "langsam");
        PlanExercise second = exercise(plan, 2L, "ex2", "Dips", 1, null);
        first.updateTraining(82.5, 4, 8);

        PlanExportResponse response = PlanExportResponse.from(plan, List.of(first, second));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Push");
        assertThat(response.createdAt()).isEqualTo(plan.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(plan.getUpdatedAt());
        assertThat(response.exercises()).extracting(PlanExerciseResponse::name)
                .containsExactly("Bench Press", "Dips");

        PlanExerciseResponse mapped = response.exercises().get(0);
        assertThat(mapped.id()).isEqualTo(1L);
        assertThat(mapped.externalId()).isEqualTo("ex1");
        assertThat(mapped.gifUrl()).isEqualTo("http://gif/ex1");
        assertThat(mapped.primaryMuscleGroups()).containsExactly("pectorals");
        assertThat(mapped.secondaryMuscleGroups()).containsExactly("triceps");
        assertThat(mapped.position()).isZero();
        assertThat(mapped.note()).isEqualTo("langsam");
        assertThat(mapped.weight()).isEqualTo(82.5);
        assertThat(mapped.sets()).isEqualTo(4);
        assertThat(mapped.reps()).isEqualTo(8);
        assertThat(response.exercises().get(1).note()).isNull();
    }

    @Test
    void fromMapsMissingTrainingValuesToNull() {
        Plan plan = plan();

        PlanExportResponse response = PlanExportResponse.from(plan,
                List.of(exercise(plan, 1L, "ex1", "Bench Press", 0, null)));

        PlanExerciseResponse mapped = response.exercises().get(0);
        assertThat(mapped.weight()).isNull();
        assertThat(mapped.sets()).isNull();
        assertThat(mapped.reps()).isNull();
    }

    @Test
    void fromWithoutExercisesGivesEmptyList() {
        PlanExportResponse response = PlanExportResponse.from(plan(), List.of());

        assertThat(response.exercises()).isNotNull().isEmpty();
    }
}
