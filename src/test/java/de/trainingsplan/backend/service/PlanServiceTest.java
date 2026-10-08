package de.trainingsplan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.PlanRequest;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.User;
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

    private PlanRepository planRepository;
    private PlanService planService;
    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        planService = new PlanService(planRepository);
        user = userWithId("max", 1L);
        otherUser = userWithId("anna", 2L);
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
