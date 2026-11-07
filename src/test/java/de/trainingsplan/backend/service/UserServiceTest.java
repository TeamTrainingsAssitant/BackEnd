package de.trainingsplan.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.trainingsplan.backend.controller.dto.PlanExportResponse;
import de.trainingsplan.backend.controller.dto.RegisterRequest;
import de.trainingsplan.backend.controller.dto.UserExportResponse;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.PlanExercise;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.AuthTokenRepository;
import de.trainingsplan.backend.repository.PlanExerciseRepository;
import de.trainingsplan.backend.repository.PlanRepository;
import de.trainingsplan.backend.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private PlanRepository planRepository;
    private AuthTokenRepository authTokenRepository;
    private PlanExerciseRepository planExerciseRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        planRepository = mock(PlanRepository.class);
        authTokenRepository = mock(AuthTokenRepository.class);
        planExerciseRepository = mock(PlanExerciseRepository.class);
        userService = new UserService(userRepository, passwordEncoder, planRepository, authTokenRepository,
                planExerciseRepository);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void assertRejected(RegisterRequest request, HttpStatus status, String message) {
        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(status);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerStoresBcryptHashAndNormalizedEmail() {
        userService.register(new RegisterRequest(" max ", " Max@Mosbach.de ", "super-safe"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getUsername()).isEqualTo("max");
        assertThat(user.getEmail()).isEqualTo("max@mosbach.de");
        assertThat(user.getPasswordHash()).isNotEqualTo("super-safe").startsWith("$2");
        assertThat(passwordEncoder.matches("super-safe", user.getPasswordHash())).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateEmailIsConflict() {
        when(userRepository.existsByEmail("max@mosbach.de")).thenReturn(true);

        assertRejected(new RegisterRequest("max", "MAX@mosbach.de", "super-safe"),
                HttpStatus.CONFLICT, "Email already registered.");
    }

    @Test
    void raceOnUniqueConstraintIsConflict() {
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> userService.register(new RegisterRequest("max", "max@mosbach.de", "super-safe")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Email already registered.");
                });
    }

    @Test
    void invalidEmailIsBadRequest() {
        assertRejected(new RegisterRequest("max", "not-an-email", "super-safe"),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
        assertRejected(new RegisterRequest("max", null, "super-safe"),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
    }

    @Test
    void shortOrMissingPasswordIsBadRequest() {
        assertRejected(new RegisterRequest("max", "max@mosbach.de", "short"),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
        assertRejected(new RegisterRequest("max", "max@mosbach.de", null),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
    }

    @Test
    void passwordOverBcryptLimitIsBadRequest() {
        assertRejected(new RegisterRequest("max", "max@mosbach.de", "a".repeat(73)),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
    }

    @Test
    void missingUsernameIsBadRequest() {
        assertRejected(new RegisterRequest(" ", "max@mosbach.de", "super-safe"),
                HttpStatus.BAD_REQUEST, "Username missing.");
        assertRejected(new RegisterRequest(null, "max@mosbach.de", "super-safe"),
                HttpStatus.BAD_REQUEST, "Username missing.");
        assertRejected(null, HttpStatus.BAD_REQUEST, "Username missing.");
    }

    @Test
    void registerReturnsTheSavedUser() {
        User user = userService.register(new RegisterRequest("max", "max@mosbach.de", "super-safe"));

        assertThat(user.getUsername()).isEqualTo("max");
        assertThat(user.getEmail()).isEqualTo("max@mosbach.de");
    }

    @Test
    void passwordLengthBoundaryIs8Characters() {
        assertRejected(new RegisterRequest("max", "max@mosbach.de", "1234567"),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");

        assertThat(userService.register(new RegisterRequest("max", "max@mosbach.de", "12345678"))).isNotNull();
    }

    @Test
    void passwordByteBoundaryIs72Bytes() {
        assertRejected(new RegisterRequest("max", "max@mosbach.de", "a".repeat(73)),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");

        assertThat(userService.register(new RegisterRequest("max", "max@mosbach.de", "a".repeat(72)))).isNotNull();
    }

    @Test
    void passwordLimitCountsBytesNotCharacters() {
        // "ä" hat 2 Bytes in UTF-8: 36 Zeichen = 72 Bytes (ok), 40 Zeichen = 80 Bytes (zu lang)
        assertRejected(new RegisterRequest("max", "max@mosbach.de", "ä".repeat(40)),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");

        assertThat(userService.register(new RegisterRequest("max", "max@mosbach.de", "ä".repeat(36)))).isNotNull();
    }

    @Test
    void passwordIsStoredExactlyAsGivenWithoutTrimming() {
        userService.register(new RegisterRequest("max", "max@mosbach.de", "  secret1  "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(passwordEncoder.matches("  secret1  ", saved.getValue().getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("secret1", saved.getValue().getPasswordHash())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "max@mosbach", "@mosbach.de", "max@@mosbach.de", "max@mosbach.",
            "max @mosbach.de", "max@mos bach.de"})
    void malformedEmailsAreRejected(String email) {
        assertRejected(new RegisterRequest("max", email, "super-safe"),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");
    }

    @Test
    void usernameIsCheckedBeforeEmailAndPassword() {
        assertRejected(new RegisterRequest(" ", "not-an-email", "x"), HttpStatus.BAD_REQUEST, "Username missing.");
    }

    @Test
    void invalidInputIsRejectedBeforeAskingTheDatabase() {
        assertRejected(new RegisterRequest("max", "not-an-email", "super-safe"),
                HttpStatus.BAD_REQUEST, "Email or password invalid.");

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void duplicateEmailIsRejectedBeforePasswordIsHashed() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserService service = new UserService(userRepository, encoder, planRepository, authTokenRepository,
                planExerciseRepository);
        when(userRepository.existsByEmail("max@mosbach.de")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("max", "max@mosbach.de", "super-safe")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(encoder);
    }

    private User userWithPassword(long id, String password) {
        User user = new User("max", "max@mosbach.de", passwordEncoder.encode(password));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Plan planWithId(User user, long id, String name) {
        Plan plan = new Plan(user, name);
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    private PlanExercise exercise(Plan plan, String externalId, int position) {
        return new PlanExercise(plan, externalId, "Name " + externalId, null, List.of("pectorals"), List.of(),
                position, null);
    }

    private void assertPasswordWrong(User user, String password) {
        assertThatThrownBy(() -> userService.deleteAccount(user, password))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Password wrong.");
                });
        verifyNoInteractions(planRepository, authTokenRepository);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteAccountRemovesTokensPlansAndUserInThisOrder() {
        User user = userWithPassword(7L, "super-safe");
        List<Plan> plans = List.of(planWithId(user, 1L, "Push"), planWithId(user, 2L, "Pull"));
        when(planRepository.findByUserIdOrderByUpdatedAtDesc(7L)).thenReturn(plans);

        userService.deleteAccount(user, "super-safe");

        // Reihenfolge wegen der Fremdschlüssel: erst Tokens und Pläne, zuletzt der Benutzer
        InOrder order = inOrder(authTokenRepository, planRepository, userRepository);
        order.verify(authTokenRepository).deleteByUserId(7L);
        order.verify(planRepository).deleteAll(plans);
        order.verify(planRepository).flush();
        order.verify(userRepository).delete(user);
    }

    @Test
    void deleteAccountWithWrongPasswordIsBadRequestAndDeletesNothing() {
        assertPasswordWrong(userWithPassword(7L, "super-safe"), "other-password");
    }

    @Test
    void deleteAccountWithMissingPasswordIsBadRequestAndDeletesNothing() {
        assertPasswordWrong(userWithPassword(7L, "super-safe"), null);
    }

    @Test
    void deleteAccountWithoutPlansStillDeletesUser() {
        User user = userWithPassword(7L, "super-safe");
        when(planRepository.findByUserIdOrderByUpdatedAtDesc(7L)).thenReturn(List.of());

        userService.deleteAccount(user, "super-safe");

        verify(planRepository).deleteAll(List.of());
        verify(userRepository).delete(user);
    }

    @Test
    void deleteAccountOnlyLooksUpPlansAndTokensOfThatUser() {
        User user = userWithPassword(7L, "super-safe");

        userService.deleteAccount(user, "super-safe");

        verify(planRepository).findByUserIdOrderByUpdatedAtDesc(7L);
        verify(authTokenRepository).deleteByUserId(7L);
    }

    @Test
    void exportReturnsUserAndPlansWithTheirExercises() {
        User user = userWithPassword(7L, "super-safe");
        Plan push = planWithId(user, 1L, "Push");
        Plan pull = planWithId(user, 2L, "Pull");
        when(planRepository.findByUserIdOrderByUpdatedAtDesc(7L)).thenReturn(List.of(push, pull));
        when(planExerciseRepository.findByPlanIdOrderByPositionAsc(1L))
                .thenReturn(List.of(exercise(push, "ex1", 0), exercise(push, "ex2", 1)));
        when(planExerciseRepository.findByPlanIdOrderByPositionAsc(2L))
                .thenReturn(List.of(exercise(pull, "ex3", 0)));

        UserExportResponse export = userService.export(user);

        assertThat(export.user().userId()).isEqualTo(7L);
        assertThat(export.user().username()).isEqualTo("max");
        assertThat(export.user().email()).isEqualTo("max@mosbach.de");
        assertThat(export.plans()).hasSize(2);
        assertThat(export.plans().get(0).exercises()).extracting(e -> e.externalId())
                .containsExactly("ex1", "ex2");
        assertThat(export.plans().get(1).exercises()).extracting(e -> e.externalId())
                .containsExactly("ex3");
    }

    @Test
    void exportKeepsPlanOrderFromRepository() {
        User user = userWithPassword(7L, "super-safe");
        when(planRepository.findByUserIdOrderByUpdatedAtDesc(7L))
                .thenReturn(List.of(planWithId(user, 2L, "Pull"), planWithId(user, 1L, "Push")));
        when(planExerciseRepository.findByPlanIdOrderByPositionAsc(any())).thenReturn(List.of());

        UserExportResponse export = userService.export(user);

        assertThat(export.plans()).extracting(PlanExportResponse::name).containsExactly("Pull", "Push");
    }

    @Test
    void exportWithoutPlansHasEmptyPlanList() {
        User user = userWithPassword(7L, "super-safe");
        when(planRepository.findByUserIdOrderByUpdatedAtDesc(7L)).thenReturn(List.of());

        UserExportResponse export = userService.export(user);

        assertThat(export.user().userId()).isEqualTo(7L);
        assertThat(export.plans()).isNotNull().isEmpty();
        verifyNoInteractions(planExerciseRepository);
    }

    @Test
    void exportAsksExercisesPerPlanId() {
        User user = userWithPassword(7L, "super-safe");
        when(planRepository.findByUserIdOrderByUpdatedAtDesc(7L))
                .thenReturn(List.of(planWithId(user, 1L, "Push"), planWithId(user, 2L, "Pull")));
        when(planExerciseRepository.findByPlanIdOrderByPositionAsc(any())).thenReturn(List.of());

        userService.export(user);

        verify(planExerciseRepository).findByPlanIdOrderByPositionAsc(1L);
        verify(planExerciseRepository).findByPlanIdOrderByPositionAsc(2L);
    }
}
