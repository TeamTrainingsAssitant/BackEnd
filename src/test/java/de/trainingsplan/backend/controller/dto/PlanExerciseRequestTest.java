package de.trainingsplan.backend.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlanExerciseRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private void assertSingleViolation(PlanExerciseRequest request, String property) {
        Set<ConstraintViolation<PlanExerciseRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString(property);
    }

    @Test
    void trainingValuesAreOptional() {
        assertThat(validator.validate(new PlanExerciseRequest("01qpYSe", null, null, null, null))).isEmpty();
    }

    @Test
    void validTrainingValuesHaveNoViolations() {
        assertThat(validator.validate(new PlanExerciseRequest("01qpYSe", "Sitz 4", 82.5, 4, 8))).isEmpty();
    }

    @Test
    void validIndividualSetsCanBeProvidedWhenAddingAnExercise() {
        assertThat(validator.validate(new PlanExerciseRequest("01qpYSe", null, null, null, null,
                java.util.List.of(new TrainingSetRequest(80.0, 10))))).isEmpty();
    }

    @Test
    void weightBoundsAreInclusive() {
        assertThat(validator.validate(new PlanExerciseRequest("01qpYSe", null, 0.0, null, null))).isEmpty();
        assertThat(validator.validate(new PlanExerciseRequest("01qpYSe", null, 1000.0, null, null))).isEmpty();
    }

    @Test
    void weightOutsideBoundsIsRejected() {
        assertSingleViolation(new PlanExerciseRequest("01qpYSe", null, -0.1, null, null), "weight");
        assertSingleViolation(new PlanExerciseRequest("01qpYSe", null, 1000.1, null, null), "weight");
    }

    @Test
    void setsBoundsAreInclusive() {
        assertThat(validator.validate(new PlanExerciseRequest("01qpYSe", null, null, 1, null))).isEmpty();
        assertThat(validator.validate(new PlanExerciseRequest("01qpYSe", null, null, 20, null))).isEmpty();
    }

    @Test
    void setsOutsideBoundsAreRejected() {
        assertSingleViolation(new PlanExerciseRequest("01qpYSe", null, null, 0, null), "sets");
        assertSingleViolation(new PlanExerciseRequest("01qpYSe", null, null, 21, null), "sets");
    }

    @Test
    void repsBoundsAreInclusive() {
        assertThat(validator.validate(new PlanExerciseRequest("01qpYSe", null, null, null, 1))).isEmpty();
        assertThat(validator.validate(new PlanExerciseRequest("01qpYSe", null, null, null, 100))).isEmpty();
    }

    @Test
    void repsOutsideBoundsAreRejected() {
        assertSingleViolation(new PlanExerciseRequest("01qpYSe", null, null, null, 0), "reps");
        assertSingleViolation(new PlanExerciseRequest("01qpYSe", null, null, null, 101), "reps");
    }

    @Test
    void blankExternalIdIsStillRejectedWithTrainingValues() {
        assertSingleViolation(new PlanExerciseRequest("  ", null, 82.5, 4, 8), "externalId");
    }
}
