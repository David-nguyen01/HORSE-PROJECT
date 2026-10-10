package com.racehorse.horse.controller;

import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.service.HorseService;
import com.racehorse.auth.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HorseSearchRequestBindingTest {

    @Test
    void searchParametersDeclareNamesExplicitly() throws Exception {
        Method search = HorseController.class.getDeclaredMethod(
                "search", String.class, HorseStatus.class, boolean.class, Pageable.class, Jwt.class, Authentication.class);

        assertThat(search.getParameters()[0].getAnnotation(RequestParam.class).name()).isEqualTo("query");
        assertThat(search.getParameters()[1].getAnnotation(RequestParam.class).name()).isEqualTo("status");
    }

    @Test
    void horseDetailPathDeclaresIdExplicitly() throws Exception {
        Method getById = HorseController.class.getDeclaredMethod(
                "getById", java.util.UUID.class, Jwt.class, Authentication.class);

        assertThat(getById.getParameters()[0].getAnnotation(PathVariable.class).value()).isEqualTo("id");
    }

    @Test
    void staffCannotDisablePaidFilterOnHorseList() {
        HorseService service = mock(HorseService.class);
        HorseController controller = new HorseController(service);
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(UUID.randomUUID().toString()).build();
        Authentication staff = mock(Authentication.class);
        when(staff.getAuthorities()).thenAnswer(inv -> List.of(new SimpleGrantedAuthority("ROLE_STABLE_STAFF")));

        controller.search(null, null, false, Pageable.unpaged(), jwt, staff);

        verify(service).search(null, null, true, Pageable.unpaged());
    }

    @Test
    void horseDetailUsesRoleSpecificAccessScope() {
        HorseService service = mock(HorseService.class);
        HorseController controller = new HorseController(service);
        UUID horseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(userId.toString()).build();
        Authentication vet = mock(Authentication.class);
        when(vet.getAuthorities()).thenAnswer(inv -> List.of(new SimpleGrantedAuthority("ROLE_VETERINARIAN")));

        controller.getById(horseId, jwt, vet);

        verify(service).getByIdForUser(horseId, userId, Role.VETERINARIAN);
    }
}
