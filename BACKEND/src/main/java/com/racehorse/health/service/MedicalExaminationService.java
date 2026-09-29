package com.racehorse.health.service;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.health.dto.*;
import com.racehorse.health.entity.MedicalExamination;
import com.racehorse.health.repository.MedicalExaminationRepository;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MedicalExaminationService {

    private final MedicalExaminationRepository repository;
    private final HorseAccessService horseAccessService;
    private final UserAccountRepository userRepository;
    private final JdbcTemplate jdbc;

    public MedicalExaminationService(
            MedicalExaminationRepository repository,
            HorseAccessService horseAccessService,
            UserAccountRepository userRepository,
            JdbcTemplate jdbc) {
        this.repository = repository;
        this.horseAccessService = horseAccessService;
        this.userRepository = userRepository;
        this.jdbc = jdbc;
    }

    // FR09 – Ghi hồ sơ khám mới, đồng thời cập nhật health_status ngựa
    @Transactional
    public MedicalExaminationResponse create(UUID veterinarianId, UUID horseId,
                                              CreateMedicalExaminationRequest request) {
        Horse horse = horseAccessService.requireActive(horseId);
        UserAccount vet = userRepository.findById(veterinarianId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản bác sĩ: " + veterinarianId));

        MedicalExamination exam = new MedicalExamination(
                horse, vet,
                request.examinationDate(),
                request.symptoms(),
                request.diagnosis(),
                request.treatmentPlan(),
                request.medicationAndDosage(),
                request.followUpDate(),
                request.healthStatusAfter(),
                Instant.now());
        repository.save(exam);

        // Cập nhật trạng thái sức khỏe ngựa ngay sau khi lưu (FR09 yêu cầu)
        jdbc.update("UPDATE dbo.horses SET health_status = ? WHERE id = ?",
                request.healthStatusAfter(), horseId);

        return toResponse(exam, List.of());
    }

    // FR09 – Thêm phụ lục (addendum) vào bản ghi đã tồn tại
    @Transactional
    public MedicalExaminationResponse addAddendum(UUID authorId, UUID examinationId,
                                                   AddExaminationAddendumRequest request) {
        repository.findById(examinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ khám: " + examinationId));
        UserAccount author = userRepository.findById(authorId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + authorId));

        UUID addendumId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO dbo.medical_examination_addenda(id,examination_id,authored_by_user_id,content,created_at) VALUES(?,?,?,?,?)",
                addendumId, examinationId, authorId, request.content(), OffsetDateTime.now());

        MedicalExamination exam = repository.findWithDetailsById(examinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ khám: " + examinationId));
        List<AddendumRow> addenda = fetchAddenda(examinationId);
        return toResponse(exam, addenda);
    }

    // FR09 – Lịch sử khám (chỉ đọc, theo thứ tự thời gian)
    @Transactional(readOnly = true)
    public Page<MedicalExaminationResponse> listForHorse(UUID horseId, UUID requesterId, boolean privileged, Pageable pageable) {
        horseAccessService.requireReadable(horseId, requesterId, privileged);
        return repository.findByHorseIdOrderByExaminationDateDescSubmittedAtDesc(horseId, pageable)
                .map(e -> toResponse(e, List.of())); // addenda loaded lazily only when viewing detail
    }

    // FR09 – Xem chi tiết một lần khám kèm phụ lục
    @Transactional(readOnly = true)
    public MedicalExaminationResponse getById(UUID examinationId, UUID requesterId, boolean privileged) {
        MedicalExamination exam = repository.findWithDetailsById(examinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ khám: " + examinationId));
        horseAccessService.requireReadable(exam.getHorse().getId(), requesterId, privileged);
        List<AddendumRow> addenda = fetchAddenda(examinationId);
        return toResponse(exam, addenda);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private record AddendumRow(UUID id, String content, UUID authorId, String authorName, OffsetDateTime createdAt) {}

    private List<AddendumRow> fetchAddenda(UUID examinationId) {
        return jdbc.query("""
                SELECT a.id, a.content, u.id AS author_id, u.username, a.created_at
                FROM dbo.medical_examination_addenda a
                JOIN dbo.users u ON u.id = a.authored_by_user_id
                WHERE a.examination_id = ?
                ORDER BY a.created_at
                """,
                (rs, i) -> new AddendumRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("content"),
                        rs.getObject("author_id", UUID.class),
                        rs.getString("username"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                examinationId);
    }

    private MedicalExaminationResponse toResponse(MedicalExamination e, List<AddendumRow> addenda) {
        var horse = new MedicalExaminationResponse.VetSummary(e.getHorse().getId(), e.getHorse().getName());
        var vet   = new MedicalExaminationResponse.VetSummary(e.getVeterinarian().getId(), e.getVeterinarian().getUsername());
        var addendaDto = addenda.stream()
                .map(a -> new MedicalExaminationResponse.AddendumResponse(
                        a.id(), a.content(),
                        new MedicalExaminationResponse.VetSummary(a.authorId(), a.authorName()),
                        a.createdAt().toInstant()))
                .toList();
        return new MedicalExaminationResponse(
                e.getId(), horse, vet,
                e.getExaminationDate(),
                e.getSymptoms(), e.getDiagnosis(), e.getTreatmentPlan(),
                e.getMedicationAndDosage(), e.getFollowUpDate(),
                e.getHealthStatusAfter(), e.getSubmittedAt(),
                addendaDto);
    }
}
