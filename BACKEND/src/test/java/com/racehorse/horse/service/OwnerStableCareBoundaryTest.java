package com.racehorse.horse.service;

import com.racehorse.caretask.controller.CareTaskController;
import com.racehorse.caretask.dto.CareTaskResponse;
import com.racehorse.caretask.service.CareTaskService;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.incident.controller.IncidentReportController;
import com.racehorse.incident.dto.IncidentReportResponse;
import com.racehorse.incident.service.IncidentReportService;
import com.racehorse.inventory.controller.InventoryController;
import com.racehorse.nutrition.controller.NutritionOverviewController;
import com.racehorse.stable.controller.StableController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OwnerStableCareBoundaryTest {
    @Test
    void wholeClubResourcesDenyHorseOwner() {
        assertOwnerDenied(StableController.class, "list", "get");
        assertOwnerDenied(NutritionOverviewController.class, "getStandardPlans", "getHorsesActive", "getStablesWorkload");
        assertOwnerDenied(InventoryController.class, "listItems", "getItem", "listUsage", "listReplenishments", "getReplenishment");
    }

    @Test
    void careTaskPhotoRequiresPermission() {
        String rule = Arrays.stream(CareTaskController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("getTaskPhoto"))
                .findFirst().orElseThrow().getAnnotation(PreAuthorize.class).value();
        assertThat(rule).contains("PERM_STABLE_CARE_VIEW");
    }

    @Test
    void ownerCannotReadCareTaskOrPhotoForOtherHorse() {
        HorseAccessService horseAccess = mock(HorseAccessService.class);
        CareTaskService service = mock(CareTaskService.class);
        CareTaskController controller = new CareTaskController(service, new OwnerHorseReadGuard(horseAccess));
        UUID taskId = UUID.randomUUID();
        UUID foreignHorseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        CareTaskResponse task = mock(CareTaskResponse.class);
        when(task.horseId()).thenReturn(foreignHorseId);
        when(service.getTask(taskId)).thenReturn(task);
        doThrow(new ResourceNotFoundException("not owned"))
                .when(horseAccess).requireReadable(foreignHorseId, ownerId, false);

        assertThatThrownBy(() -> controller.getTask(taskId, jwt(ownerId), owner()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> controller.getTaskPhoto(taskId, jwt(ownerId), owner()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(service, never()).getTaskPhoto(taskId);
        assertThatThrownBy(() -> controller.listTasks(null, null, null, null, null, jwt(ownerId), owner()))
                .isInstanceOf(BusinessException.class);
        verify(service, never()).listTasks(any(), any(), any(), any(), any());
    }

    @Test
    void ownerCannotReadIncidentForOtherHorseOrListAll() {
        HorseAccessService horseAccess = mock(HorseAccessService.class);
        IncidentReportService service = mock(IncidentReportService.class);
        IncidentReportController controller = new IncidentReportController(service, new OwnerHorseReadGuard(horseAccess));
        UUID incidentId = UUID.randomUUID();
        UUID foreignHorseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        IncidentReportResponse report = mock(IncidentReportResponse.class);
        when(report.horseId()).thenReturn(foreignHorseId);
        when(service.getReport(incidentId)).thenReturn(report);
        doThrow(new ResourceNotFoundException("not owned"))
                .when(horseAccess).requireReadable(foreignHorseId, ownerId, false);

        assertThatThrownBy(() -> controller.getReport(incidentId, jwt(ownerId), owner()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> controller.listReports(null, null, null, null, null, jwt(ownerId), owner()))
                .isInstanceOf(BusinessException.class);
        verify(service, never()).listReports(any(), any(), any(), any(), any());
    }

    private void assertOwnerDenied(Class<?> controller, String... methods) {
        for (String name : methods) {
            String rule = Arrays.stream(controller.getDeclaredMethods())
                    .filter(method -> method.getName().equals(name))
                    .findFirst().orElseThrow().getAnnotation(PreAuthorize.class).value();
            assertThat(rule).contains("!hasRole('HORSE_OWNER')");
        }
    }

    private Authentication owner() {
        return new UsernamePasswordAuthenticationToken("owner", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_HORSE_OWNER")));
    }

    private Jwt jwt(UUID userId) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", userId.toString()));
    }
}
