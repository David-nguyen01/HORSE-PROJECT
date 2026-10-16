package com.racehorse.reporting.controller;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.GlobalExceptionHandler;
import com.racehorse.reporting.service.OwnerReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OwnerReportControllerTest {

    @Mock
    private OwnerReportService service;

    private MockMvc mockMvc;
    private final UUID actorId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OwnerReportController controller = new OwnerReportController(service);

        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(Jwt.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return Jwt.withTokenValue("mock-token")
                        .header("alg", "none")
                        .subject(actorId.toString())
                        .build();
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    @Test
    void generateReturns201CreatedWhenSuccessful() throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        UUID reportId = UUID.randomUUID();

        OwnerReportService.ReportView view = new OwnerReportService.ReportView(
                reportId, ownerId, "owner1", start, end, OffsetDateTime.now(), actorId,
                List.of(), List.of(), "VND", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(service.generate(eq(ownerId), eq(start), eq(end), eq(actorId)))
                .thenReturn(view);

        String json = """
                {
                    "ownerId": "%s",
                    "periodStart": "2026-09-01",
                    "periodEnd": "2026-09-30"
                }
                """.formatted(ownerId);

        mockMvc.perform(post("/api/owner-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reportId.toString()))
                .andExpect(jsonPath("$.ownerUsername").value("owner1"))
                .andExpect(jsonPath("$.currency").value("VND"));
    }

    @Test
    void generateReturns409ConflictWhenDuplicateReportExceptionOccurs() throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        when(service.generate(eq(ownerId), eq(start), eq(end), eq(actorId)))
                .thenThrow(new BusinessException("Báo cáo cho kỳ này với nội dung tương đương đã tồn tại"));

        String json = """
                {
                    "ownerId": "%s",
                    "periodStart": "2026-09-01",
                    "periodEnd": "2026-09-30"
                }
                """.formatted(ownerId);

        mockMvc.perform(post("/api/owner-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Báo cáo cho kỳ này với nội dung tương đương đã tồn tại"));
    }

    @Test
    void generateReturns400BadRequestWhenRequestBodyMissingFields() throws Exception {
        mockMvc.perform(post("/api/owner-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
