package de.trainingsplan.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlanTest {

    private final User user = new User("max", "max@mosbach.de", "hash");

    @Test
    void newPlanStartsWithEqualCreatedAndUpdatedTime() {
        LocalDateTime before = LocalDateTime.now();

        Plan plan = new Plan(user, "Push");

        assertThat(plan.getUser()).isSameAs(user);
        assertThat(plan.getName()).isEqualTo("Push");
        assertThat(plan.getCreatedAt()).isBetween(before, LocalDateTime.now());
        assertThat(plan.getUpdatedAt()).isEqualTo(plan.getCreatedAt());
    }

    @Test
    void updateChangesNameAndRefreshesUpdatedAt() {
        Plan plan = new Plan(user, "Push");
        LocalDateTime longAgo = LocalDateTime.now().minusDays(1);
        ReflectionTestUtils.setField(plan, "createdAt", longAgo);
        ReflectionTestUtils.setField(plan, "updatedAt", longAgo);

        plan.update("Pull");

        assertThat(plan.getName()).isEqualTo("Pull");
        assertThat(plan.getCreatedAt()).isEqualTo(longAgo);
        assertThat(plan.getUpdatedAt()).isAfter(longAgo);
    }

}
