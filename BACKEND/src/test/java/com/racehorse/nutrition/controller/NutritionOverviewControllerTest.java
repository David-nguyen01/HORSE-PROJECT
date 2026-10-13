package com.racehorse.nutrition.controller;

import com.racehorse.nutrition.service.NutritionPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionOverviewControllerTest {

    @Mock
    private NutritionPlanService service;

    private NutritionOverviewController controller;

    @BeforeEach
    void setUp() {
        controller = new NutritionOverviewController(service);
    }

    @Test
    void classAnnotationsAreCorrect() {
        assertThat(NutritionOverviewController.class.getAnnotation(RestController.class)).isNotNull();
        RequestMapping mapping = NutritionOverviewController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/nutrition");
        assertThat(NutritionOverviewController.class.getAnnotation(PreAuthorize.class)).isNull();
    }

    @Test
    void methodLevelRbacEnforcesStableCareViewAndExcludesHorseOwner() throws Exception {
        Method standardPlansMethod = NutritionOverviewController.class.getDeclaredMethod("getStandardPlans");
        PreAuthorize spAuth = standardPlansMethod.getAnnotation(PreAuthorize.class);
        assertThat(spAuth).isNotNull();
        assertThat(spAuth.value()).isEqualTo("hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')");
        assertThat(standardPlansMethod.getAnnotation(GetMapping.class).value()).containsExactly("/standard-plans");

        Method horsesActiveMethod = NutritionOverviewController.class.getDeclaredMethod("getHorsesActive", LocalDate.class);
        PreAuthorize haAuth = horsesActiveMethod.getAnnotation(PreAuthorize.class);
        assertThat(haAuth).isNotNull();
        assertThat(haAuth.value()).isEqualTo("hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')");
        assertThat(horsesActiveMethod.getAnnotation(GetMapping.class).value()).containsExactly("/horses-active");

        Method stablesWorkloadMethod = NutritionOverviewController.class.getDeclaredMethod("getStablesWorkload", LocalDate.class);
        PreAuthorize swAuth = stablesWorkloadMethod.getAnnotation(PreAuthorize.class);
        assertThat(swAuth).isNotNull();
        assertThat(swAuth.value()).isEqualTo("hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')");
        assertThat(stablesWorkloadMethod.getAnnotation(GetMapping.class).value()).containsExactly("/stables-workload");
    }

    @Test
    void getStandardPlansDelegatesToService() {
        List<Map<String, Object>> mockPlans = List.of(Map.of("id", "plan-1", "name", "Standard Diet"));
        when(service.listStandardPlans()).thenReturn(mockPlans);

        List<Map<String, Object>> result = controller.getStandardPlans();

        assertThat(result).isSameAs(mockPlans);
        verify(service).listStandardPlans();
    }

    @Test
    void getHorsesActiveDelegatesToService() {
        LocalDate date = LocalDate.of(2026, 10, 12);
        List<Map<String, Object>> mockHorses = List.of(Map.of("horseCode", "H-001", "status", "ASSIGNED"));
        when(service.listHorsesActivePlans(date)).thenReturn(mockHorses);

        List<Map<String, Object>> result = controller.getHorsesActive(date);

        assertThat(result).isSameAs(mockHorses);
        verify(service).listHorsesActivePlans(date);
    }

    @Test
    void getStablesWorkloadDelegatesToService() {
        LocalDate date = LocalDate.of(2026, 10, 12);
        Map<String, Object> mockWorkload = Map.of("summary", Map.of("totalStalls", 5));
        when(service.getStableWorkload(date)).thenReturn(mockWorkload);

        Map<String, Object> result = controller.getStablesWorkload(date);

        assertThat(result).isSameAs(mockWorkload);
        verify(service).getStableWorkload(date);
    }
}
