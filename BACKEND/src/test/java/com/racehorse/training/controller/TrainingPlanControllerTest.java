package com.racehorse.training.controller;

import com.racehorse.training.dto.CopyTrainingPlanRequest;
import com.racehorse.training.dto.TrainingPlanRequest;
import com.racehorse.training.service.TrainingPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingPlanControllerTest {

    @Mock
    private TrainingPlanService service;

    private TrainingPlanController controller;

    @BeforeEach
    void setUp() {
        controller = new TrainingPlanController(service);
    }

    @Test
    void classLevelDoesNotHavePreAuthorizeBlockingViewPermissions() {
        assertThat(TrainingPlanController.class.getAnnotation(PreAuthorize.class)).isNull();
    }

    @Test
    void methodLevelPreAuthorizeMatchesSrsRbac() throws Exception {
        Method createMethod = TrainingPlanController.class.getDeclaredMethod("create", Jwt.class, TrainingPlanRequest.class);
        PreAuthorize createAuth = createMethod.getAnnotation(PreAuthorize.class);
        assertThat(createAuth).isNotNull();
        assertThat(createAuth.value()).isEqualTo("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')");

        Method updateMethod = TrainingPlanController.class.getDeclaredMethod("update", UUID.class, TrainingPlanRequest.class);
        PreAuthorize updateAuth = updateMethod.getAnnotation(PreAuthorize.class);
        assertThat(updateAuth).isNotNull();
        assertThat(updateAuth.value()).isEqualTo("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')");

        Method copyMethod = TrainingPlanController.class.getDeclaredMethod("copy", UUID.class, Jwt.class, CopyTrainingPlanRequest.class);
        PreAuthorize copyAuth = copyMethod.getAnnotation(PreAuthorize.class);
        assertThat(copyAuth).isNotNull();
        assertThat(copyAuth.value()).isEqualTo("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')");

        Method getMethod = TrainingPlanController.class.getDeclaredMethod("get", UUID.class, Jwt.class, Authentication.class);
        PreAuthorize getAuth = getMethod.getAnnotation(PreAuthorize.class);
        assertThat(getAuth).isNotNull();
        assertThat(getAuth.value()).isEqualTo("hasAuthority('PERM_TRAINING_VIEW')");

        Method listMethod = TrainingPlanController.class.getDeclaredMethod("listForHorse", UUID.class, Jwt.class, Authentication.class);
        PreAuthorize listAuth = listMethod.getAnnotation(PreAuthorize.class);
        assertThat(listAuth).isNotNull();
        assertThat(listAuth.value()).isEqualTo("hasAuthority('PERM_TRAINING_VIEW')");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void getResolvesPrivilegedStatusCorrectlyForStaffAndOwner() {
        UUID planId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(userId.toString());

        Authentication ownerAuth = mock(Authentication.class);
        when(ownerAuth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_HORSE_OWNER")));

        controller.get(planId, jwt, ownerAuth);
        verify(service).get(planId, userId, false);

        Authentication vetAuth = mock(Authentication.class);
        when(vetAuth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_VETERINARIAN")));

        controller.get(planId, jwt, vetAuth);
        verify(service).get(planId, userId, true);
    }
}
