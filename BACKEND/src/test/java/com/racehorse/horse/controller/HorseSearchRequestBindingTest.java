package com.racehorse.horse.controller;

import com.racehorse.horse.entity.HorseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class HorseSearchRequestBindingTest {

    @Test
    void searchParametersDeclareNamesExplicitly() throws Exception {
        Method search = HorseController.class.getDeclaredMethod(
                "search", String.class, HorseStatus.class, Pageable.class, Jwt.class, Authentication.class);

        assertThat(search.getParameters()[0].getAnnotation(RequestParam.class).name()).isEqualTo("query");
        assertThat(search.getParameters()[1].getAnnotation(RequestParam.class).name()).isEqualTo("status");
    }
}
