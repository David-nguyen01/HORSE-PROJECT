package com.racehorse.reporting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.reporting.dto.OwnerReportResponse;
import com.racehorse.reporting.entity.OwnerReport;
import com.racehorse.reporting.repository.OwnerReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private final OwnerReportRepository ownerReportRepository;
    private final ObjectMapper mapper;

    public ReportingService(OwnerReportRepository ownerReportRepository, ObjectMapper mapper) {
        this.ownerReportRepository = ownerReportRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<OwnerReportResponse> getOwnerReports(UUID ownerId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return ownerReportRepository.findReports(ownerId, startDate, endDate, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OwnerReportResponse getOwnerReportDetail(UUID id, UUID ownerId) {
        OwnerReport report = ownerReportRepository.findById(id)
                .filter(r -> r.getOwner().getId().equals(ownerId) || ownerId == null) // null ownerId allows admin access
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo: " + id));
        return toResponse(report);
    }

    @Transactional(readOnly = true)
    public String exportOwnerReportHtml(UUID id, UUID ownerId) {
        OwnerReportResponse report = getOwnerReportDetail(id, ownerId);
        // Note: Co-owner cost allocation is pending business decision. Currently shows full amounts.
        return """
                <html>
                <head><title>Báo cáo chủ ngựa - %s</title></head>
                <body>
                    <h1>Báo cáo chủ ngựa</h1>
                    <p>Mã báo cáo: %s</p>
                    <p>Kỳ báo cáo: %s đến %s</p>
                    <p><i>Lưu ý: Báo cáo đang hiển thị toàn bộ chi phí/doanh thu (chưa áp dụng tỷ lệ đồng sở hữu do chờ quyết định nghiệp vụ).</i></p>
                    <hr/>
                    <h2>Ngựa</h2>
                    <p>%d mã ngựa</p>
                    <h2>Giao dịch</h2>
                    <p>%d giao dịch</p>
                </body>
                </html>
                """.formatted(
                        report.periodEnd(),
                        report.id(),
                        report.periodStart(),
                        report.periodEnd(),
                        report.horseIds().size(),
                        report.transactionIds().size()
        );
    }

    private OwnerReportResponse toResponse(OwnerReport report) {
        return new OwnerReportResponse(
                report.getId(),
                report.getOwner().getId(),
                report.getPeriodStart(),
                report.getPeriodEnd(),
                report.getGeneratedAt(),
                report.getGeneratedBy().getId(),
                report.getPdfFileId(),
                parseIds(report.getHorseIdsJson()),
                parseIds(report.getTransactionIdsJson())
        );
    }

    private List<UUID> parseIds(String json) {
        try {
            List<Map<String, String>> list = mapper.readValue(json, new TypeReference<>() {});
            return list.stream().map(m -> UUID.fromString(m.get("id"))).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
