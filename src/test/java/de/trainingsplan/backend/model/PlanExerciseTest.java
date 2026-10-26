package de.trainingsplan.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlanExerciseTest {

    private PlanExercise exercise() {
        Plan plan = new Plan(new User("max", "max@mosbach.de", "hash"), "Push", null);
        return new PlanExercise(plan, "ex1", "Bench Press", "http://gif/ex1",
                List.of("pectorals"), List.of("triceps"), 2, "langsam");
    }

    @Test
    void newExerciseHasNoTrainingValues() {
        PlanExercise exercise = exercise();

        assertThat(exercise.getWeight()).isNull();
        assertThat(exercise.getSets()).isNull();
        assertThat(exercise.getReps()).isNull();
    }

    @Test
    void updateTrainingSetsValues() {
        PlanExercise exercise = exercise();

        exercise.updateTraining(82.5, 4, 8);

        assertThat(exercise.getWeight()).isEqualTo(82.5);
        assertThat(exercise.getSets()).isEqualTo(4);
        assertThat(exercise.getReps()).isEqualTo(8);
    }

    @Test
    void updateTrainingWithNullClearsValues() {
        PlanExercise exercise = exercise();
        exercise.updateTraining(82.5, 4, 8);

        exercise.updateTraining(null, null, null);

        assertThat(exercise.getWeight()).isNull();
        assertThat(exercise.getSets()).isNull();
        assertThat(exercise.getReps()).isNull();
    }

    @Test
    void replaceExerciseDetailsKeepsTrainingValuesNoteAndPosition() {
        PlanExercise exercise = exercise();
        exercise.updateTraining(60.0, 3, 10);

        exercise.replaceExerciseDetails("ex2", "Dips", "http://gif/ex2", List.of("triceps"), List.of("pectorals"));

        assertThat(exercise.getExternalId()).isEqualTo("ex2");
        assertThat(exercise.getName()).isEqualTo("Dips");
        assertThat(exercise.getGifUrl()).isEqualTo("http://gif/ex2");
        assertThat(exercise.getPrimaryMuscleGroups()).containsExactly("triceps");
        assertThat(exercise.getSecondaryMuscleGroups()).containsExactly("pectorals");
        assertThat(exercise.getWeight()).isEqualTo(60.0);
        assertThat(exercise.getSets()).isEqualTo(3);
        assertThat(exercise.getReps()).isEqualTo(10);
        assertThat(exercise.getNote()).isEqualTo("langsam");
        assertThat(exercise.getPosition()).isEqualTo(2);
    }
}
