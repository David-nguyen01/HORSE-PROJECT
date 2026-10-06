package com.racehorse.horse.controller;

import com.racehorse.horse.dto.CreateHorseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class HorseCreateAuthorizationTest {

    @Test
    void creatingHorseIsRestrictedToClubManagers() throws Exception {
        Method create = HorseController.class.getDeclaredMethod("create", CreateHorseRequest.class);

        assertThat(create.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('PERM_PROFILE_EDIT') and hasRole('CLUB_MANAGER')");
    }
}
