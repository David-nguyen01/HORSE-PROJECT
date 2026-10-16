package com.racehorse.racing.controller;

import com.racehorse.horse.controller.HorseController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RaceRoleBoundaryTest {

    @Test
    void onlyClubManagerCanCreateArchiveOrTransferHorseOwnership() {
        for (String method : new String[]{"create", "replaceOwnership", "archive"}) {
            String rule = authorization(HorseController.class, method);
            assertThat(rule).contains("hasRole('CLUB_MANAGER')").doesNotContain("HEAD_TRAINER");
        }
    }

    @Test
    void onlyClubManagerCanModifyRaceEvents() {
        for (String method : new String[]{"update", "deactivate", "complete"}) {
            String rule = authorization(RaceEventController.class, method);
            assertThat(rule).contains("hasRole('CLUB_MANAGER')").doesNotContain("HEAD_TRAINER");
        }
    }

    @Test
    void ownerDecisionIsTheOnlyApprovalEndpoint() {
        assertThat(Arrays.stream(RaceEventController.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("confirm"))).isTrue();
        assertThat(authorization(RaceEventController.class, "decide"))
                .contains("hasRole('HORSE_OWNER')", "PERM_RACING_APPROVE");
    }

    private String authorization(Class<?> controller, String methodName) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        return method.getAnnotation(PreAuthorize.class).value();
    }
}
