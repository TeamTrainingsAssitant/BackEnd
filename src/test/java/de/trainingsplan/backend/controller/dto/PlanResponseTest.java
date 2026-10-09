package de.trainingsplan.backend.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlanResponseTest {

    private Plan plan() {
        Plan plan = new Plan(new User("max", "max@mosbach.de", "hash"), "Push", "Brust");
        ReflectionTestUtils.setField(plan, "id", 10L);
        return plan;
    }

    @Test
    void fromMapsAllFields() {
        Plan plan = plan();

        PlanResponse response = PlanResponse.from(plan);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Push");
        assertThat(response.description()).isEqualTo("Brust");
        assertThat(response.createdAt()).isEqualTo(plan.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(plan.getUpdatedAt());
    }

    @Test
    void listItemFromMapsFieldsAndExerciseCount() {
        Plan plan = plan();

        PlanListItemResponse response = PlanListItemResponse.from(plan, 3);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Push");
        assertThat(response.description()).isEqualTo("Brust");
        assertThat(response.updatedAt()).isEqualTo(plan.getUpdatedAt());
        assertThat(response.exerciseCount()).isEqualTo(3);
    }
}
