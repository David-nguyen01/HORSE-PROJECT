package com.racehorse.caretask.controller;

import com.racehorse.incident.controller.IncidentReportController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class StableCareMutationBoundaryTest {
    @Test
    void onlyManagerOrStableStaffCanCompleteCareTasks() {
        for (String method : new String[]{"completeTaskJson", "completeTaskMultipart"}) {
            String rule = authorization(CareTaskController.class, method);
            assertThat(rule).contains("hasRole", "STABLE_STAFF", "CLUB_MANAGER")
                    .doesNotContain("HEAD_TRAINER");
        }
    }

    @Test
    void staffCannotCreateOrCancelAssignments() {
        for (String method : new String[]{"createTask", "cancelTask"}) {
            String rule = authorization(CareTaskController.class, method);
            assertThat(rule).contains("CLUB_MANAGER", "HEAD_TRAINER")
                    .doesNotContain("STABLE_STAFF");
        }
    }

    @Test
    void incidentStatusRequiresExplicitOperationalRole() {
        for (String method : new String[]{"updateStatus", "acknowledge", "dismiss"}) {
            String rule = authorization(IncidentReportController.class, method);
            assertThat(rule).contains("CLUB_MANAGER", "VETERINARIAN", "STABLE_STAFF")
                    .doesNotContain("HEAD_TRAINER", "HORSE_OWNER");
        }
    }

    private String authorization(Class<?> controller, String methodName) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst().orElseThrow().getAnnotation(PreAuthorize.class).value();
    }
}
