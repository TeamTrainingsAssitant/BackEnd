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

class PlanExerciseUpdateRequestTest {

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

    private void assertSingleViolation(PlanExerciseUpdateRequest request, String property) {
        Set<ConstraintViolation<PlanExerciseUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString(property);
    }

    @Test
    void allValuesAreOptional() {
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, null, null, null))).isEmpty();
    }

    @Test
    void validTrainingValuesHaveNoViolations() {
        assertThat(validator.validate(new PlanExerciseUpdateRequest("Sitz 4", 82.5, 4, 8))).isEmpty();
    }

    @Test
    void partialLegacyTrainingValuesAreAllowed() {
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, 82.5, null, null))).isEmpty();
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, null, 4, null))).isEmpty();
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, null, null, 8))).isEmpty();
    }

    @Test
    void validIndividualSetsHaveNoViolations() {
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, null, null, null, java.util.List.of(
                new TrainingSetRequest(80.0, 10), new TrainingSetRequest(70.0, 8))))).isEmpty();
    }

    @Test
    void emptyTrainingSetsAreAllowedToClearTrainingValues() {
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, null, null, null, java.util.List.of())))
                .isEmpty();
    }

    @Test
    void weightAndRepsAreRequiredInEverySet() {
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, null, null, null,
                java.util.List.of(new TrainingSetRequest(null, 10)))))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath())
                        .hasToString("trainingSets[0].weight"));
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, null, null, null,
                java.util.List.of(new TrainingSetRequest(80.0, null)))))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath())
                        .hasToString("trainingSets[0].reps"));
    }

    @Test
    void weightBoundsAreInclusive() {
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, 0.0, 1, 1))).isEmpty();
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, 1000.0, 1, 1))).isEmpty();
    }

    @Test
    void weightOutsideBoundsIsRejected() {
        assertSingleViolation(new PlanExerciseUpdateRequest(null, -0.1, 1, 1), "weight");
        assertSingleViolation(new PlanExerciseUpdateRequest(null, 1000.1, 1, 1), "weight");
    }

    @Test
    void setsBoundsAreInclusive() {
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, 0.0, 1, 1))).isEmpty();
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, 0.0, 20, 1))).isEmpty();
    }

    @Test
    void setsOutsideBoundsAreRejected() {
        assertSingleViolation(new PlanExerciseUpdateRequest(null, 0.0, 0, 1), "sets");
        assertSingleViolation(new PlanExerciseUpdateRequest(null, 0.0, 21, 1), "sets");
    }

    @Test
    void repsBoundsAreInclusive() {
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, 0.0, 1, 1))).isEmpty();
        assertThat(validator.validate(new PlanExerciseUpdateRequest(null, 0.0, 1, 100))).isEmpty();
    }

    @Test
    void repsOutsideBoundsAreRejected() {
        assertSingleViolation(new PlanExerciseUpdateRequest(null, 0.0, 1, 0), "reps");
        assertSingleViolation(new PlanExerciseUpdateRequest(null, 0.0, 1, 101), "reps");
    }
}
