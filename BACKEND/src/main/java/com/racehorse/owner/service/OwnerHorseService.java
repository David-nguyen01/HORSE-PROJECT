package com.racehorse.owner.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseOwnership;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.mapper.HorseMapper;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.horse.repository.HorseRepository;
import com.racehorse.owner.dto.OwnerCreateHorseRequest;
import com.racehorse.owner.dto.OwnerUpdateHorseRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service xử lý nghiệp vụ Owner tạo/sửa/submit ngựa.
 * <p>
 * Luồng trạng thái ngựa do Owner thao tác:
 * <pre>
 *   DRAFT → (submit) → PENDING_PAYMENT → (thanh toán) → PENDING_MEDICAL → ...
 * </pre>
 * <p>
 * Mã ngựa (code) được tự động sinh theo format: OWN-yyyyMMdd-XXXX
 * (OWN = Owner-submitted, yyyyMMdd = ngày tạo, XXXX = 4 ký tự hex ngẫu nhiên).
 */
@Service
public class OwnerHorseService {

    private final HorseRepository horseRepository;
    private final HorseOwnershipRepository ownershipRepository;
    private final UserAccountRepository userRepository;
    private final HorseMapper horseMapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OwnerHorseService(
            HorseRepository horseRepository,
            HorseOwnershipRepository ownershipRepository,
            UserAccountRepository userRepository,
            HorseMapper horseMapper,
            JdbcTemplate jdbc,
            ObjectMapper json) {
        this.horseRepository = horseRepository;
        this.ownershipRepository = ownershipRepository;
        this.userRepository = userRepository;
        this.horseMapper = horseMapper;
        this.jdbc = jdbc;
        this.json = json;
    }

    // ─── TẠO NGỰA MỚI ────────────────────────────────────────────

    /**
     * Owner tạo hồ sơ ngựa ở trạng thái DRAFT.
     * Hệ thống tự sinh code, gán status=ACTIVE (Hibernate enum, đánh dấu DRAFT qua paymentStatus),
     * stableLocation=PENDING_ASSIGNMENT, paymentStatus=UNPAID.
     * Tự động tạo sở hữu 100% cho owner.
     */
    @Transactional
    public HorseResponse createDraft(UUID ownerId, OwnerCreateHorseRequest request) {
        // 1. Xác minh user là HORSE_OWNER đang hoạt động
        UserAccount owner = requireOwnerAccount(ownerId);

        // 2. Kiểm tra chip/passport trùng
        String chip = normalizeNullable(request.microchipPassportNumber());
        if (chip != null && horseRepository.existsByMicrochipPassportNumberIgnoreCase(chip)) {
            throw new BusinessException("Số chip/hộ chiếu đã tồn tại: " + chip);
        }

        // 3. Sinh mã ngựa tự động
        String code = generateCode();

        // 4. Kiểm tra ngựa bố/mẹ (nếu chọn từ hệ thống)
        Horse sire = findOptionalParent(request.sireId(), "Không tìm thấy ngựa bố");
        Horse dam = findOptionalParent(request.damId(), "Không tìm thấy ngựa mẹ");
        validateDistinctParents(request.sireId(), request.damId());
        
        validateParentAge(sire, request.birthDate(), "Ngựa cha");
        validateParentAge(dam, request.birthDate(), "Ngựa mẹ");
        validateManualParentAge(request.sireManualInfo(), request.birthDate(), "Ngựa cha (nhập tay)");
        validateManualParentAge(request.damManualInfo(), request.birthDate(), "Ngựa mẹ (nhập tay)");

        // 5. Tạo entity ngựa với giá trị mặc định
        Horse horse = new Horse(
                code,
                request.name().trim(),
                chip,
                request.birthDate(),
                request.gender(),
                request.colorMarkings().trim(),
                request.breed().trim(),
                "PENDING_ASSIGNMENT",      // stableLocation mặc định
                HorseStatus.ACTIVE         // status enum (ACTIVE = hồ sơ hợp lệ, phân biệt bằng paymentStatus)
        );
        horse.assignParents(sire, dam);
        // paymentStatus mặc định đã là UNPAID trong entity

        Horse saved = horseRepository.save(horse);

        // 6. Tự động tạo sở hữu 100%
        HorseOwnership ownership = new HorseOwnership(saved, owner, new BigDecimal("100.00"));
        ownershipRepository.save(ownership);

        // 7. Lưu thông vị cha/mẹ thủ công (nếu có) vào module_records
        saveManualPedigree(saved.getId(), ownerId, request.sireManualInfo(), request.damManualInfo());

        return horseMapper.toResponse(saved, List.of(ownership));
    }

    // ─── CẬP NHẬT NGỰA DRAFT ────────────────────────────────────

    /**
     * Owner cập nhật hồ sơ ngựa chỉ khi paymentStatus = UNPAID (tức DRAFT).
     */
    @Transactional
    public HorseResponse updateDraft(UUID ownerId, UUID horseId, OwnerUpdateHorseRequest request) {
        Horse horse = requireOwnedDraft(ownerId, horseId);

        // Kiểm tra chip trùng
        String chip = normalizeNullable(request.microchipPassportNumber());
        if (chip != null && horseRepository.existsByMicrochipPassportNumberIgnoreCaseAndIdNot(chip, horseId)) {
            throw new BusinessException("Số chip/hộ chiếu đã tồn tại: " + chip);
        }

        // Kiểm tra ngựa bố/mẹ
        Horse sire = findOptionalParent(request.sireId(), "Không tìm thấy ngựa bố");
        Horse dam = findOptionalParent(request.damId(), "Không tìm thấy ngựa mẹ");
        validateDistinctParents(request.sireId(), request.damId());
        
        validateParentAge(sire, request.birthDate(), "Ngựa cha");
        validateParentAge(dam, request.birthDate(), "Ngựa mẹ");
        validateManualParentAge(request.sireManualInfo(), request.birthDate(), "Ngựa cha (nhập tay)");
        validateManualParentAge(request.damManualInfo(), request.birthDate(), "Ngựa mẹ (nhập tay)");

        horse.update(
                request.name().trim(),
                chip,
                request.birthDate(),
                request.gender(),
                request.colorMarkings().trim(),
                request.breed().trim(),
                horse.getStableLocation(), // giữ nguyên
                horse.getStatus()          // giữ nguyên
        );
        horse.assignParents(sire, dam);

        // Cập nhật thông tin cha/mẹ thủ công
        saveManualPedigree(horseId, ownerId, request.sireManualInfo(), request.damManualInfo());

        return toResponse(horse);
    }

    // ─── SUBMIT (GỬI HỒ SƠ) ─────────────────────────────────────

    /**
     * Owner gửi hồ sơ ngựa từ DRAFT (UNPAID) → PENDING_PAYMENT.
     * Sau bước này, owner cần thanh toán phí dịch vụ.
     */
    @Transactional
    public HorseResponse submit(UUID ownerId, UUID horseId) {
        Horse horse = requireOwnedDraft(ownerId, horseId);

        // Đánh dấu chờ thanh toán bằng cách đổi paymentStatus
        // (PENDING_PAYMENT sẽ được frontend hiểu là "đã nộp, chờ trả tiền")
        horse.setPaymentStatus("PENDING_PAYMENT");

        return toResponse(horse);
    }

    // ─── XEM CHI TIẾT NGỰA CỦA OWNER ────────────────────────────

    /**
     * Lấy thông tin phả hệ thủ công (nếu có) của ngựa.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getManualPedigree(UUID horseId) {
        List<Map<String, String>> rows = jdbc.query(
                "select data_json from module_records where module_code='PROFILE' and record_type='UNVERIFIED_PEDIGREE' "
                        + "and horse_id=? and status='ACTIVE' order by created_at desc",
                (rs, n) -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> map = json.readValue(rs.getString("data_json"), Map.class);
                        return map;
                    } catch (JacksonException e) {
                        return Map.<String, String>of();
                    }
                },
                horseId
        );
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    // ─── HÀM NỘI BỘ ─────────────────────────────────────────────

    /**
     * Sinh mã ngựa tự động: OWN-yyyyMMdd-XXXX
     */
    private String generateCode() {
        String datePart = java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = Integer.toHexString(
                java.util.concurrent.ThreadLocalRandom.current().nextInt(0x10000))
                .toUpperCase();
        // Đảm bảo 4 ký tự
        while (randomPart.length() < 4) {
            randomPart = "0" + randomPart;
        }
        String code = "OWN-" + datePart + "-" + randomPart;

        // Đảm bảo không trùng
        if (horseRepository.existsByCodeIgnoreCase(code)) {
            return generateCode(); // đệ quy thử lại (xác suất trùng cực thấp)
        }
        return code;
    }

    /**
     * Yêu cầu ngựa phải thuộc sở hữu của owner và ở trạng thái DRAFT (paymentStatus = UNPAID).
     */
    private Horse requireOwnedDraft(UUID ownerId, UUID horseId) {
        Horse horse = horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));

        if (!ownershipRepository.existsByHorseIdAndOwnerId(horseId, ownerId)) {
            throw new ResourceNotFoundException("Bạn không sở hữu ngựa này: " + horseId);
        }

        if (!"UNPAID".equalsIgnoreCase(horse.getPaymentStatus())) {
            throw new BusinessException("Chỉ được sửa hồ sơ ngựa khi chưa gửi đăng ký (trạng thái Nháp)");
        }

        return horse;
    }

    private UserAccount requireOwnerAccount(UUID ownerId) {
        UserAccount user = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + ownerId));
        if (!user.isEnabled() || !user.getRoles().contains(Role.HORSE_OWNER)) {
            throw new BusinessException("Tài khoản phải là HORSE_OWNER đang hoạt động");
        }
        return user;
    }

    private void saveManualPedigree(UUID horseId, UUID ownerId, String sireInfo, String damInfo) {
        boolean hasSire = sireInfo != null && !sireInfo.isBlank();
        boolean hasDam = damInfo != null && !damInfo.isBlank();
        if (!hasSire && !hasDam) return;

        // Xóa bản ghi cũ (nếu có)
        jdbc.update(
                "update module_records set status='SUPERSEDED', updated_at=SYSDATETIMEOFFSET() "
                        + "where module_code='PROFILE' and record_type='UNVERIFIED_PEDIGREE' and horse_id=? and status='ACTIVE'",
                horseId);

        // Ghi bản ghi mới
        Map<String, String> data = new LinkedHashMap<>();
        if (hasSire) data.put("sireManualInfo", sireInfo.trim());
        if (hasDam) data.put("damManualInfo", damInfo.trim());

        jdbc.update(
                "insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,data_json,created_at,updated_at) "
                        + "values(?,'PROFILE','UNVERIFIED_PEDIGREE','api',?,?,'ACTIVE',?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                UUID.randomUUID(), horseId, ownerId, toJson(data));
    }

    private void validateDistinctParents(UUID sireId, UUID damId) {
        if (sireId != null && sireId.equals(damId)) {
            throw new BusinessException("Ngựa bố và ngựa mẹ phải là hai hồ sơ khác nhau");
        }
    }

    private void validateParentAge(Horse parent, java.time.LocalDate childBirthDate, String parentType) {
        if (parent == null || parent.getBirthDate() == null || childBirthDate == null) return;
        java.time.LocalDate parentDOB = parent.getBirthDate();
        if (!parentDOB.isBefore(childBirthDate)) {
            throw new BusinessException(parentType + " phải sinh trước ngựa con.");
        }
        if (parentDOB.plusYears(4).isAfter(childBirthDate)) {
            throw new BusinessException(parentType + " chưa đủ 4 tuổi tại thời điểm ngựa con sinh ra.");
        }
    }

    private void validateManualParentAge(String manualInfoJson, java.time.LocalDate childBirthDate, String parentType) {
        if (manualInfoJson == null || manualInfoJson.isBlank()) return;
        try {
            var map = json.readValue(manualInfoJson, Map.class);
            Object dobObj = map.get("birthDate");
            if (dobObj != null && !dobObj.toString().isBlank()) {
                java.time.LocalDate parentDOB = java.time.LocalDate.parse(dobObj.toString());
                if (!parentDOB.isBefore(childBirthDate)) {
                    throw new BusinessException(parentType + " phải sinh trước ngựa con.");
                }
                if (parentDOB.plusYears(4).isAfter(childBirthDate)) {
                    throw new BusinessException(parentType + " chưa đủ 4 tuổi tại thời điểm ngựa con sinh ra.");
                }
            }
        } catch (Exception e) {
            // Bỏ qua nếu dữ liệu cũ không phải JSON hoặc sai định dạng ngày
        }
    }

    private Horse findOptionalParent(UUID id, String errorMessage) {
        if (id == null) return null;
        return horseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(errorMessage + ": " + id));
    }

    private HorseResponse toResponse(Horse horse) {
        return horseMapper.toResponse(
                horse,
                ownershipRepository.findByHorseIdOrderByOwnerUsernameAsc(horse.getId()));
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Không thể tạo dữ liệu JSON", e);
        }
    }
}
