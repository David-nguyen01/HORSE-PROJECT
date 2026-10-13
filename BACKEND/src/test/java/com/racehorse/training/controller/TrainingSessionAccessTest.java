package com.racehorse.training.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import com.racehorse.training.service.TrainingEvaluationService;
import com.racehorse.training.service.TrainingScheduleService;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(TrainingSessionAccessTest.SecurityTestConfiguration.class)
class TrainingSessionAccessTest {
    private static final String TRAINING_WRITE =
            "hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')";
    private static final String OWNER_READ =
            "hasAuthority('PERM_TRAINING_VIEW') and hasRole('HORSE_OWNER')";

    @Autowired private TrainingScheduleController scheduleController;
    @Autowired private TrainingEvaluationController evaluationController;
    @Autowired private OwnerTrainingScheduleController ownerScheduleController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static final String TRAINER_ONLY_WRITE = "hasAuthority('PERM_TRAINING_EDIT') and hasRole('HEAD_TRAINER')";
    private static final String SCHEDULE_UPDATE = "hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')";
    private static final String PREPARATION_READY = "hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','STABLE_STAFF')";

    @Test
    void headTrainerCanPerformAllExecutionMutationsAndEvaluation() {
        UUID scheduleId = UUID.randomUUID();
        Jwt jwt = jwt();
        authenticate("HEAD_TRAINER", "PERM_TRAINING_EDIT");

        assertThatCode(() -> scheduleController.update(scheduleId, jwt, null, authentication())).doesNotThrowAnyException();
        assertThatCode(() -> scheduleController.cancel(scheduleId, jwt, authentication())).doesNotThrowAnyException();
        assertThatCode(() -> scheduleController.start(scheduleId, jwt)).doesNotThrowAnyException();
        assertThatCode(() -> scheduleController.finish(scheduleId, jwt)).doesNotThrowAnyException();
        assertThatCode(() -> evaluationController.create(jwt, null, authentication())).doesNotThrowAnyException();

        assertPolicy(TrainingScheduleController.class, "update", SCHEDULE_UPDATE);
        assertPolicy(TrainingScheduleController.class, "cancel", SCHEDULE_UPDATE);
        assertPolicy(TrainingScheduleController.class, "start", TRAINER_ONLY_WRITE);
        assertPolicy(TrainingScheduleController.class, "finish", TRAINER_ONLY_WRITE);
        assertPolicy(TrainingScheduleController.class, "ready", PREPARATION_READY);
        assertPolicy(TrainingEvaluationController.class, "create", TRAINER_ONLY_WRITE);
    }

    @Test
    void allowsClubManagerToUpdateAndCancelSchedulesButNotExecuteOrEvaluate() {
        UUID scheduleId = UUID.randomUUID();
        Jwt jwt = jwt();
        authenticate("CLUB_MANAGER", "PERM_TRAINING_EDIT");

        assertThatCode(() -> scheduleController.update(scheduleId, jwt, null, authentication())).doesNotThrowAnyException();
        assertThatCode(() -> scheduleController.cancel(scheduleId, jwt, authentication())).doesNotThrowAnyException();
        assertDenied(() -> scheduleController.start(scheduleId, jwt));
        assertDenied(() -> scheduleController.finish(scheduleId, jwt));
        assertDenied(() -> evaluationController.create(jwt, null, authentication()));
    }

    @Test
    void deniesSessionMutationsToEveryNonTrainerNonManagerRole() {
        UUID scheduleId = UUID.randomUUID();
        Jwt jwt = jwt();
        for (String role : List.of("STABLE_STAFF", "VETERINARIAN", "HORSE_OWNER")) {
            authenticate(role, "PERM_TRAINING_EDIT");
            assertDenied(() -> scheduleController.update(scheduleId, jwt, null, authentication()));
            assertDenied(() -> scheduleController.cancel(scheduleId, jwt, authentication()));
            assertDenied(() -> scheduleController.start(scheduleId, jwt));
            assertDenied(() -> scheduleController.finish(scheduleId, jwt));
            assertDenied(() -> evaluationController.create(jwt, null, authentication()));
        }
    }

    // Deferred mutation workflows are documented in BACKEND/TRAINING_TEST_SCOPE.md.
    @Test
    void managersAndTrainersCanCreatePublishButOnlyTrainersCanEvaluate() {
        UUID scheduleId = UUID.randomUUID();
        Jwt jwt = jwt();
        for (String role : List.of("HEAD_TRAINER", "CLUB_MANAGER")) {
            authenticate(role, "PERM_TRAINING_EDIT");
            assertThatCode(() -> scheduleController.create(jwt, null, authentication())).doesNotThrowAnyException();
            assertThatCode(() -> scheduleController.publish(scheduleId, jwt, authentication())).doesNotThrowAnyException();
            if (role.equals("HEAD_TRAINER"))
                assertThatCode(() -> evaluationController.create(jwt, null, authentication())).doesNotThrowAnyException();
            else assertDenied(() -> evaluationController.create(jwt, null, authentication()));
        }
        assertPolicy(TrainingScheduleController.class, "create", TRAINING_WRITE);
        assertPolicy(TrainingScheduleController.class, "publish", TRAINING_WRITE);
        assertPolicy(TrainingEvaluationController.class, "create", "hasAuthority('PERM_TRAINING_EDIT') and hasRole('HEAD_TRAINER')");
    }

    @Test
    void otherRolesCannotCreatePublishOrEvaluateEvenWithEditPermission() {
        UUID scheduleId = UUID.randomUUID();
        Jwt jwt = jwt();
        for (String role : List.of("STABLE_STAFF", "VETERINARIAN", "HORSE_OWNER")) {
            authenticate(role, "PERM_TRAINING_EDIT");
            assertDenied(() -> scheduleController.create(jwt, null, authentication()));
            assertDenied(() -> scheduleController.publish(scheduleId, jwt, authentication()));
            assertDenied(() -> evaluationController.create(jwt, null, authentication()));
        }
    }

    @Test
    void managerAndTrainerRolesStillRequireEditPermission() {
        UUID scheduleId = UUID.randomUUID();
        Jwt jwt = jwt();
        for (String role : List.of("HEAD_TRAINER", "CLUB_MANAGER")) {
            authenticate(role, "PERM_TRAINING_VIEW");
            assertDenied(() -> scheduleController.create(jwt, null, authentication()));
            assertDenied(() -> scheduleController.publish(scheduleId, jwt, authentication()));
            assertDenied(() -> evaluationController.create(jwt, null, authentication()));
        }
    }

    @Test
    void staffCanReadIndividualSchedulesButNotTheClubCalendar() {
        Jwt jwt = jwt();
        authenticate("STABLE_STAFF", "PERM_TRAINING_VIEW");
        assertThatCode(() -> scheduleController.get(UUID.randomUUID(), jwt, authentication()))
                .doesNotThrowAnyException();
        assertDenied(() -> scheduleController.calendar(null, null, null, null, jwt, authentication()));
    }
    @Test
    void ownerScheduleReadsAreOwnerOnly() {
        UUID horseId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Jwt jwt = jwt();

        authenticate("HORSE_OWNER", "PERM_TRAINING_VIEW");
        assertThatCode(() -> ownerScheduleController.listForHorse(horseId, null, null, jwt))
                .doesNotThrowAnyException();
        assertThatCode(() -> ownerScheduleController.getOwnerView(scheduleId, jwt))
                .doesNotThrowAnyException();

        authenticate("HEAD_TRAINER", "PERM_TRAINING_VIEW");
        assertDenied(() -> ownerScheduleController.listForHorse(horseId, null, null, jwt));
        assertDenied(() -> ownerScheduleController.getOwnerView(scheduleId, jwt));
    }

    @Test
    void usesDedicatedOwnerOnlyScheduleReadsAndKeepsStaffReadsSeparate() throws Exception {
        assertPolicy(OwnerTrainingScheduleController.class, "listForHorse", OWNER_READ);
        assertPolicy(OwnerTrainingScheduleController.class, "getOwnerView", OWNER_READ);
        assertPolicy(TrainingScheduleController.class, "get",
                "hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','STABLE_STAFF')");
        assertPolicy(TrainingScheduleController.class, "calendar",
                "hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')");
    }

    private void authenticate(String role, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-user", "n/a", List.of(
                        new SimpleGrantedAuthority("ROLE_" + role),
                        new SimpleGrantedAuthority(authority))));
    }

    private org.springframework.security.core.Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Jwt jwt() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(UUID.randomUUID().toString());
        return jwt;
    }

    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(AccessDeniedException.class);
    }

    private void assertPolicy(Class<?> controller, String methodName, String expected) {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("%s.%s authorization annotation", controller.getSimpleName(), methodName)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }

    @Configuration
    @EnableMethodSecurity
    static class SecurityTestConfiguration {
        @Bean TrainingScheduleService trainingScheduleService() { return mock(TrainingScheduleService.class); }
        @Bean TrainingEvaluationService trainingEvaluationService() { return mock(TrainingEvaluationService.class); }
        @Bean TrainingScheduleController trainingScheduleController(TrainingScheduleService service) {
            return new TrainingScheduleController(service);
        }
        @Bean TrainingEvaluationController trainingEvaluationController(TrainingEvaluationService service) {
            return new TrainingEvaluationController(service);
        }
        @Bean OwnerTrainingScheduleController ownerTrainingScheduleController(TrainingScheduleService service) {
            return new OwnerTrainingScheduleController(service);
        }
    }
}
