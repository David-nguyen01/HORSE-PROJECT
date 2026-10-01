package com.racehorse.horse.service;

import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.repository.HorseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service lấy thông tin phân công "4 người" cho ngựa:
 * <ul>
 *   <li>Owner: từ bảng horse_ownerships</li>
 *   <li>Veterinarian: từ module_records (MEDICAL/MEDICAL_ASSIGNMENT, status=ACTIVE hoặc COMPLETED gần nhất)</li>
 *   <li>Trainer: từ trường currentTrainer trên horses hoặc module_records (TRAINING/TRAINER_ASSIGNMENT)</li>
 *   <li>CareStaff: từ module_records (STABLE_CARE/STALL_ASSIGNMENT, status=ACTIVE)</li>
 * </ul>
 */
@Service
public class HorseAssignmentService {

    private final JdbcTemplate jdbc;
    private final HorseRepository horseRepository;

    public HorseAssignmentService(JdbcTemplate jdbc, HorseRepository horseRepository) {
        this.jdbc = jdbc;
        this.horseRepository = horseRepository;
    }

    @Transactional(readOnly = true)
    public HorseAssignmentsResponse getAssignments(UUID horseId) {
        // Kiểm tra ngựa tồn tại
        if (!horseRepository.existsById(horseId)) {
            throw new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId);
        }

        // 1. Owner(s)
        List<PersonSummary> owners = jdbc.query(
                "select u.id, u.username, u.email from horse_ownerships ho "
                        + "join users u on u.id = ho.owner_id "
                        + "where ho.horse_id = ? order by u.username",
                (rs, n) -> new PersonSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("email"),
                        "HORSE_OWNER"
                ),
                horseId
        );

        // 2. Veterinarian (bác sĩ phân công gần nhất - ACTIVE hoặc COMPLETED)
        List<PersonSummary> vets = jdbc.query(
                "select top 1 u.id, u.username, u.email from module_records mr "
                        + "join users u on u.id = mr.user_id "
                        + "where mr.module_code='MEDICAL' and mr.record_type='MEDICAL_ASSIGNMENT' "
                        + "and mr.horse_id=? "
                        + "order by case when mr.status='ACTIVE' then 0 else 1 end, mr.created_at desc",
                (rs, n) -> new PersonSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("email"),
                        "VETERINARIAN"
                ),
                horseId
        );

        // 3. Trainer (phân công gần nhất - ACTIVE hoặc COMPLETED, hoặc từ trường currentTrainer)
        List<PersonSummary> trainers = jdbc.query(
                "select top 1 u.id, u.username, u.email from module_records mr "
                        + "join users u on u.id = mr.user_id "
                        + "where mr.module_code='TRAINING' and mr.record_type='TRAINER_ASSIGNMENT' "
                        + "and mr.horse_id=? "
                        + "order by case when mr.status='ACTIVE' then 0 else 1 end, mr.created_at desc",
                (rs, n) -> new PersonSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("email"),
                        "HEAD_TRAINER"
                ),
                horseId
        );

        // Fallback: nếu chưa có trainer assignment, lấy từ trường current_trainer_id
        if (trainers.isEmpty()) {
            trainers = jdbc.query(
                    "select u.id, u.username, u.email from horses h "
                            + "join users u on u.id = h.current_trainer_id "
                            + "where h.id = ? and h.current_trainer_id is not null",
                    (rs, n) -> new PersonSummary(
                            rs.getObject("id", UUID.class),
                            rs.getString("username"),
                            rs.getString("email"),
                            "HEAD_TRAINER"
                    ),
                    horseId
            );
        }

        // 4. CareStaff (nhân viên chăm sóc - lấy từ stall assignment active)
        List<PersonSummary> careStaff = jdbc.query(
                "select distinct u.id, u.username, u.email "
                        + "from module_records mr "
                        + "join users u on u.id = mr.user_id "
                        + "where mr.module_code='STABLE_CARE' and mr.record_type='DAILY_CARE_TASK' "
                        + "and mr.horse_id=? and mr.status in ('PENDING','COMPLETED') "
                        + "and mr.event_date >= DATEADD(day, -30, GETDATE()) "
                        + "order by u.username",
                (rs, n) -> new PersonSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("email"),
                        "STABLE_STAFF"
                ),
                horseId
        );

        return new HorseAssignmentsResponse(
                horseId,
                owners,
                vets.isEmpty() ? null : vets.getFirst(),
                trainers.isEmpty() ? null : trainers.getFirst(),
                careStaff
        );
    }

    // ─── VIEW DTO ────────────────────────────────────────────────

    public record PersonSummary(UUID id, String username, String email, String role) {
    }

    public record HorseAssignmentsResponse(
            UUID horseId,
            List<PersonSummary> owners,
            PersonSummary veterinarian,
            PersonSummary trainer,
            List<PersonSummary> careStaff
    ) {
    }
}
