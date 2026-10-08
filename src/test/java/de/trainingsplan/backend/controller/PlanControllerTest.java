package de.trainingsplan.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.MessageResponse;
import de.trainingsplan.backend.controller.dto.PlanListItemResponse;
import de.trainingsplan.backend.controller.dto.PlanRequest;
import de.trainingsplan.backend.controller.dto.PlanResponse;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.service.PlanService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

class PlanControllerTest {

    private PlanService planService;
    private PlanController controller;
    private User user;

    @BeforeEach
    void setUp() {
        planService = mock(PlanService.class);
        controller = new PlanController(planService);
        user = new User("max", "max@mosbach.de", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    private Plan planWithId(long id, String name) {
        Plan plan = new Plan(user, name, "Beschreibung");
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    @Test
    void listMapsPlansToListItemsWithZeroExercises() {
        when(planService.list(user)).thenReturn(List.of(planWithId(10L, "Push"), planWithId(11L, "Pull")));

        List<PlanListItemResponse> response = controller.list(user);

        assertThat(response).hasSize(2);
        assertThat(response).extracting(PlanListItemResponse::id).containsExactly(10L, 11L);
        assertThat(response).extracting(PlanListItemResponse::name).containsExactly("Push", "Pull");
        assertThat(response).extracting(PlanListItemResponse::exerciseCount).containsOnly(0);
    }

    @Test
    void listOfUserWithoutPlansIsEmpty() {
        when(planService.list(user)).thenReturn(List.of());

        assertThat(controller.list(user)).isEmpty();
    }

    @Test
    void createReturnsCreatedWithPlan() {
        PlanRequest request = new PlanRequest("Push", "Beschreibung");
        when(planService.create(user, request)).thenReturn(planWithId(10L, "Push"));

        ResponseEntity<PlanResponse> response = controller.create(user, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(10L);
        assertThat(response.getBody().name()).isEqualTo("Push");
    }

    @Test
    void getReturnsPlanOfService() {
        when(planService.get(user, 10L)).thenReturn(planWithId(10L, "Push"));

        PlanResponse response = controller.get(user, 10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Push");
        assertThat(response.description()).isEqualTo("Beschreibung");
    }

    @Test
    void updateReturnsUpdatedPlan() {
        PlanRequest request = new PlanRequest("Neu", "Neue Beschreibung");
        when(planService.update(user, 10L, request)).thenReturn(planWithId(10L, "Neu"));

        PlanResponse response = controller.update(user, 10L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Neu");
    }

    @Test
    void deleteCallsServiceAndReturnsMessage() {
        MessageResponse response = controller.delete(user, 10L);

        verify(planService).delete(user, 10L);
        assertThat(response.message()).isEqualTo("Plan deleted.");
    }

    @Test
    void notFoundFromServiceIsPassedThrough() {
        when(planService.get(user, 99L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));

        assertThatThrownBy(() -> controller.get(user, 99L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Plan not found.");
                });
    }
}
