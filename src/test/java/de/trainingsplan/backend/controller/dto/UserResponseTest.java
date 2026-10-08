package de.trainingsplan.backend.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import de.trainingsplan.backend.model.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserResponseTest {

    @Test
    void fromMapsAllFieldsAndHidesPasswordHash() {
        User user = new User("max", "max@mosbach.de", "secret-hash");
        ReflectionTestUtils.setField(user, "id", 5L);

        UserResponse response = UserResponse.from(user);

        assertThat(response.userId()).isEqualTo(5L);
        assertThat(response.username()).isEqualTo("max");
        assertThat(response.email()).isEqualTo("max@mosbach.de");
        assertThat(response.createdAt()).isEqualTo(LocalDate.now());
        assertThat(response.toString()).doesNotContain("secret-hash");
    }
}
