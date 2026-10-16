package com.racehorse.horse.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.dto.CreateHorseRequest;
import com.racehorse.horse.dto.HorseResponse;
import com.racehorse.horse.dto.ReplaceOwnershipRequest;
import com.racehorse.horse.dto.UpdateHorseRequest;
import com.racehorse.horse.dto.UpdateOwnedHorseRequest;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseGender;
import com.racehorse.horse.entity.HorseOwnership;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.mapper.HorseMapper;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.horse.repository.HorseRepository;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class HorseService {

    private final HorseRepository horseRepository;
    private final HorseOwnershipRepository ownershipRepository;
    private final UserAccountRepository userRepository;
    private final HorseMapper horseMapper;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    @Autowired
    public HorseService(
            HorseRepository horseRepository,
            HorseOwnershipRepository ownershipRepository,
            UserAccountRepository userRepository,
            HorseMapper horseMapper,
            Clock clock,
            JdbcTemplate jdbc,
            NotificationService notifications) {
        this.horseRepository = horseRepository;
        this.ownershipRepository = ownershipRepository;
        this.userRepository = userRepository;
        this.horseMapper = horseMapper;
        this.clock = clock;
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    public HorseService(HorseRepository horseRepository, HorseOwnershipRepository ownershipRepository,
                        UserAccountRepository userRepository, HorseMapper horseMapper, Clock clock, JdbcTemplate jdbc) {
        this(horseRepository, ownershipRepository, userRepository, horseMapper, clock, jdbc, null);
    }

    public HorseService(HorseRepository horseRepository, HorseOwnershipRepository ownershipRepository,
                        UserAccountRepository userRepository, HorseMapper horseMapper, Clock clock) {
        this(horseRepository, ownershipRepository, userRepository, horseMapper, clock, null, null);
    }

    @Transactional
    public HorseResponse create(CreateHorseRequest request) {
        if (request.currentTrainerId() != null || request.stableLocation() != null && !request.stableLocation().isBlank()) {
            throw new BusinessException("Huấn luyện viên và ô chuồng chỉ được phân công sau quy trình tiếp nhận y tế");
        }
        if (request.initialOwnerId() == null) {
            throw new BusinessException("Mỗi ngựa phải có đúng một chủ sở hữu");
        }
        validateUniqueIdentifiers(request);
        validateDistinctParents(request.sireId(), request.damId());
        validateManualParents(request.sireId(), request.sireManualName(), request.sireManualId(),
                request.damId(), request.damManualName(), request.damManualId());

        Horse sire = findOwnedParent(request.sireId(), request.initialOwnerId(), HorseGender.STALLION,
                request.birthDate(), "ngựa cha");
        Horse dam = findOwnedParent(request.damId(), request.initialOwnerId(), HorseGender.MARE,
                request.birthDate(), "ngựa mẹ");

        Horse horse = horseMapper.toEntity(request);
        horse.assignParents(sire, dam);
        horse.assignManualParents(trimToNull(request.sireManualName()), trimToNull(request.sireManualId()),
                trimToNull(request.damManualName()), trimToNull(request.damManualId()));
        Horse saved = horseRepository.save(horse);

        if (request.initialOwnerId() != null) {
            UserAccount owner = findUserWithRole(request.initialOwnerId(), Role.HORSE_OWNER, "Chủ sở hữu");
            HorseOwnership ownership = new HorseOwnership(saved, owner, new BigDecimal("100.00"));
            ownershipRepository.save(ownership);
            return horseMapper.toResponse(saved, List.of(ownership));
        }

        return toResponse(saved);
    }

    @Transactional
    public HorseResponse createForOwner(CreateHorseRequest request, UUID ownerId) {
        validateUniqueIdentifiers(request);
        if (request.currentTrainerId() != null || request.initialOwnerId() != null) {
            throw new BusinessException("Chủ ngựa không được tự gán huấn luyện viên hoặc chủ sở hữu khác");
        }
        validateDistinctParents(request.sireId(), request.damId());
        validateManualParents(request.sireId(), request.sireManualName(), request.sireManualId(),
                request.damId(), request.damManualName(), request.damManualId());
        Horse sire = findOwnedParent(request.sireId(), ownerId, HorseGender.STALLION, request.birthDate(), "ngựa cha");
        Horse dam = findOwnedParent(request.damId(), ownerId, HorseGender.MARE, request.birthDate(), "ngựa mẹ");
        UserAccount owner = findUserWithRole(ownerId, Role.HORSE_OWNER, "Chủ sở hữu");
        Horse horse = horseMapper.toEntityForOwner(request);
        horse.assignParents(sire, dam);
        horse.assignManualParents(trimToNull(request.sireManualName()), trimToNull(request.sireManualId()),
                trimToNull(request.damManualName()), trimToNull(request.damManualId()));
        Horse saved = horseRepository.save(horse);
        HorseOwnership ownership = new HorseOwnership(saved, owner, new BigDecimal("100.00"));
        ownershipRepository.save(ownership);
        return horseMapper.toResponse(saved, List.of(ownership));
    }

    @Transactional(readOnly = true)
    public HorseResponse getByIdForUser(UUID id, UUID userId, Role role) {
        Horse horse = findHorse(id);
        boolean allowed = switch (role) {
            case CLUB_MANAGER -> true;
            case HEAD_TRAINER -> horse.getArchivedAt() == null
                    && horse.getCurrentTrainer() != null && userId.equals(horse.getCurrentTrainer().getId());
            case VETERINARIAN -> horse.getArchivedAt() == null && isServiceVisible(horse)
                    && Set.of("WAITING_FOR_VET", "IN_MEDICAL", "PENDING_STALL", "STABLED").contains(horse.getIntakeStatus());
            case STABLE_STAFF -> horse.getArchivedAt() == null && isServiceVisible(horse);
            case HORSE_OWNER -> horse.getArchivedAt() == null
                    && ownershipRepository.existsByHorseIdAndOwnerId(id, userId);
        };
        if (!allowed) {
            throw new ResourceNotFoundException("Không tìm thấy ngựa được phép truy cập: " + id);
        }
        return toResponse(horse);
    }

    @Transactional(readOnly = true)
    public Page<HorseResponse> search(String query, HorseStatus status, Pageable pageable) {
        return search(query, status, false, pageable);
    }

    @Transactional(readOnly = true)
    public Page<HorseResponse> search(String query, HorseStatus status, boolean paidOnly, Pageable pageable) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return (paidOnly
                ? horseRepository.searchPaidOnly(normalizedQuery, status, serviceToday(), pageable)
                : horseRepository.search(normalizedQuery, status, pageable))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<HorseResponse> searchForTrainer(String query, HorseStatus status, UUID trainerId, Pageable pageable) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return horseRepository.searchForTrainer(normalizedQuery, status, trainerId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<HorseResponse> searchForVeterinarian(String query, HorseStatus status, Pageable pageable) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return horseRepository.searchForVeterinarian(normalizedQuery, status, serviceToday(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<HorseResponse> getEligibleForMedical() {
        return horseRepository.findEligibleForMedical(serviceToday()).stream()
                .map(this::toResponse)
                .toList();
    }

    private LocalDate serviceToday() {
        return LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    private boolean isServiceVisible(Horse horse) {
        if (Boolean.TRUE.equals(horse.getServiceOverride())) return true;
        if (!"PAID".equals(horse.getPaymentStatus())) return false;
        return horse.getPaidUntil() == null || !horse.getPaidUntil().isBefore(serviceToday());
    }

    @Transactional(readOnly = true)
    public Page<HorseResponse> findOwnedBy(UUID ownerId, Pageable pageable) {
        return horseRepository.findOwnedBy(ownerId, pageable).map(this::toResponse);
    }

    @Transactional
    public HorseResponse update(UUID id, UpdateHorseRequest request) {
        Horse horse = findHorse(id);
        UUID existingTrainerId = horse.getCurrentTrainer() == null ? null : horse.getCurrentTrainer().getId();
        if (!java.util.Objects.equals(request.currentTrainerId(), existingTrainerId)
                || !java.util.Objects.equals(request.stableLocation() == null ? "" : request.stableLocation(), horse.getStableLocation())) {
            throw new BusinessException("Không thể phân chuồng hoặc gán HLV qua API sửa hồ sơ; dùng quy trình tiếp nhận");
        }
        validateDistinctParents(request.sireId(), request.damId());
        validateManualParents(request.sireId(), request.sireManualName(), request.sireManualId(),
                request.damId(), request.damManualName(), request.damManualId());

        UUID ownerId = soleOwnerId(horse);
        boolean sireChanged = parentChanged(horse.getSire(), horse.getSireManualName(), horse.getSireManualId(),
                request.sireId(), request.sireManualName(), request.sireManualId());
        boolean damChanged = parentChanged(horse.getDam(), horse.getDamManualName(), horse.getDamManualId(),
                request.damId(), request.damManualName(), request.damManualId());
        if (sireChanged || damChanged) validateParentCycle(horse, request.sireId(), request.damId());

        String chip = normalizeNullable(request.microchipPassportNumber());
        if (chip != null
                && horseRepository.existsByMicrochipPassportNumberIgnoreCaseAndIdNot(chip, id)) {
            throw new BusinessException("Số chip/hộ chiếu đã tồn tại: " + chip);
        }

        Horse sire = sireChanged ? findEditableParent(request.sireId(), ownerId, HorseGender.STALLION,
                request.birthDate(), "ngựa cha") : horse.getSire();
        Horse dam = damChanged ? findEditableParent(request.damId(), ownerId, HorseGender.MARE,
                request.birthDate(), "ngựa mẹ") : horse.getDam();
        horse.update(
                request.name().trim(),
                chip,
                request.birthDate(),
                request.gender(),
                request.colorMarkings().trim(),
                request.breed().trim(),
                horse.getStableLocation(),
                request.status());
        horse.assignParents(sire, dam);
        horse.assignManualParents(trimToNull(request.sireManualName()), trimToNull(request.sireManualId()),
                trimToNull(request.damManualName()), trimToNull(request.damManualId()));
        notifyResponsible(horse);
        return toResponse(horse);
    }

    @Transactional
    public HorseResponse updateForOwner(UUID id, UUID ownerId, UpdateOwnedHorseRequest request) {
        Horse horse = findHorse(id);
        if (horse.getArchivedAt() != null || !ownershipRepository.existsByHorseIdAndOwnerId(id, ownerId))
            throw new ResourceNotFoundException("Không tìm thấy ngựa được phép chỉnh sửa: " + id);
        validateDistinctParents(request.sireId(), request.damId());
        validateManualParents(request.sireId(), request.sireManualName(), request.sireManualId(),
                request.damId(), request.damManualName(), request.damManualId());
        boolean sireChanged = parentChanged(horse.getSire(), horse.getSireManualName(), horse.getSireManualId(),
                request.sireId(), request.sireManualName(), request.sireManualId());
        boolean damChanged = parentChanged(horse.getDam(), horse.getDamManualName(), horse.getDamManualId(),
                request.damId(), request.damManualName(), request.damManualId());
        if (sireChanged || damChanged) validateParentCycle(horse, request.sireId(), request.damId());
        Horse sire = sireChanged ? findEditableParent(request.sireId(), ownerId, HorseGender.STALLION,
                request.birthDate(), "ngựa cha") : horse.getSire();
        Horse dam = damChanged ? findEditableParent(request.damId(), ownerId, HorseGender.MARE,
                request.birthDate(), "ngựa mẹ") : horse.getDam();
        String chip = normalizeNullable(request.microchipPassportNumber());
        if (chip != null && horseRepository.existsByMicrochipPassportNumberIgnoreCaseAndIdNot(chip, id))
            throw new BusinessException("Số chip/hộ chiếu đã tồn tại: " + chip);
        horse.update(request.name().trim(), chip, request.birthDate(), request.gender(),
                request.colorMarkings().trim(), request.breed().trim(),
                horse.getStableLocation(), horse.getStatus());
        horse.assignParents(sire, dam);
        horse.assignManualParents(trimToNull(request.sireManualName()), trimToNull(request.sireManualId()),
                trimToNull(request.damManualName()), trimToNull(request.damManualId()));
        notifyResponsible(horse);
        return toResponse(horse);
    }

    private void notifyResponsible(Horse horse) {
        if (notifications == null) return;
        UUID changeId = UUID.randomUUID();
        Set<UUID> sent = new HashSet<>();
        for (UserAccount recipient : Arrays.asList(horse.getCurrentTrainer(), horse.getCaretaker(),
                horse.getDiagnosingVet()).stream().filter(java.util.Objects::nonNull).toList()) {
            if (recipient.getId() != null && sent.add(recipient.getId()))
                notifications.createDeduplicated(recipient, "HORSE_PROFILE_UPDATED", NotificationPriority.NORMAL,
                        "Hồ sơ ngựa đã được cập nhật", horse.getCode() + " - " + horse.getName(),
                        "HORSE", horse.getId(), "HORSE_PROFILE_UPDATED_" + changeId + "_" + recipient.getId());
        }
    }

    @Transactional
    public HorseResponse replaceOwnership(UUID horseId, ReplaceOwnershipRequest request) {
        if (!request.confirmed()) {
            throw new BusinessException("Phải xác nhận trước khi thay đổi tỷ lệ sở hữu");
        }
        if (request.owners().size() != 1) {
            throw new BusinessException("Mỗi ngựa phải có đúng một chủ sở hữu");
        }
        Horse horse = findHorse(horseId);
        Set<UUID> uniqueOwnerIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ReplaceOwnershipRequest.OwnerShare share : request.owners()) {
            if (!uniqueOwnerIds.add(share.ownerId())) {
                throw new BusinessException("Một chủ sở hữu không được xuất hiện nhiều lần");
            }
            total = total.add(share.percentage());
        }
        if (total.compareTo(new BigDecimal("100.00")) != 0) {
            throw new BusinessException("Tổng tỷ lệ sở hữu phải bằng 100.00%");
        }

        List<HorseOwnership> replacements = request.owners().stream()
                .map(share -> new HorseOwnership(
                        horse,
                        findUserWithRole(share.ownerId(), Role.HORSE_OWNER, "Chủ sở hữu"),
                        share.percentage()))
                .toList();
        ownershipRepository.deleteByHorseId(horseId);
        ownershipRepository.flush();
        ownershipRepository.saveAll(replacements);
        return horseMapper.toResponse(horse, replacements);
    }

    @Transactional
    public HorseResponse archive(UUID id) {
        Horse horse = findHorse(id);
        horse.archive(clock.instant());
        return toResponse(horse);
    }

    private void validateUniqueIdentifiers(CreateHorseRequest request) {
        String code = request.code().trim();
        if (horseRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Mã ngựa đã tồn tại: " + code);
        }

        String chip = request.microchipPassportNumber();
        if (chip != null && !chip.isBlank()
                && horseRepository.existsByMicrochipPassportNumberIgnoreCase(chip.trim())) {
            throw new BusinessException("Số chip/hộ chiếu đã tồn tại: " + chip.trim());
        }
    }

    private void validateDistinctParents(UUID sireId, UUID damId) {
        if (sireId != null && sireId.equals(damId)) {
            throw new BusinessException("Ngựa bố và ngựa mẹ phải là hai hồ sơ khác nhau");
        }
    }

    private void validateManualParents(UUID sireId, String sireName, String sireManualId,
            UUID damId, String damName, String damManualId) {
        validateManualParent(sireId, sireName, sireManualId, "ngựa cha");
        validateManualParent(damId, damName, damManualId, "ngựa mẹ");
        if (sireManualId != null && damManualId != null
                && sireManualId.trim().equalsIgnoreCase(damManualId.trim()))
            throw new BusinessException("ID ngựa cha và ngựa mẹ phải khác nhau");
    }

    private boolean parentChanged(Horse linkedParent, String currentManualName, String currentManualId,
            UUID requestedParentId, String requestedManualName, String requestedManualId) {
        UUID currentParentId = linkedParent == null ? null : linkedParent.getId();
        return !java.util.Objects.equals(currentParentId, requestedParentId)
                || !java.util.Objects.equals(trimToNull(currentManualName), trimToNull(requestedManualName))
                || !java.util.Objects.equals(trimToNull(currentManualId), trimToNull(requestedManualId));
    }

    private Horse findEditableParent(UUID parentId, UUID ownerId, HorseGender gender,
            LocalDate childBirthDate, String label) {
        if (parentId == null) return null;
        if (ownerId == null)
            throw new BusinessException("Ngựa cha/mẹ được chọn phải thuộc cùng chủ sở hữu");
        return findOwnedParent(parentId, ownerId, gender, childBirthDate, label);
    }

    private UUID soleOwnerId(Horse horse) {
        List<HorseOwnership> ownerships = ownershipRepository.findByHorseIdOrderByOwnerUsernameAsc(horse.getId());
        return ownerships.size() == 1 ? ownerships.getFirst().getOwner().getId() : null;
    }

    private void validateManualParent(UUID parentId, String name, String manualId, String label) {
        boolean hasName = name != null && !name.isBlank();
        boolean hasManualId = manualId != null && !manualId.isBlank();
        if ((hasName || hasManualId) && parentId != null)
            throw new BusinessException("Chọn một cách khai báo cho " + label + ": hồ sơ có sẵn hoặc nhập tay");
        if (hasName != hasManualId)
            throw new BusinessException("Khi nhập tay " + label + ", cần nhập cả tên và ID");
    }

    private Horse findOptionalParent(UUID id, String errorMessage) {
        if (id == null) {
            return null;
        }
        return horseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(errorMessage + ": " + id));
    }

    private Horse findOwnedParent(UUID id, UUID ownerId, HorseGender expectedGender,
                                  LocalDate childBirthDate, String label) {
        if (id == null) return null;
        if (!ownershipRepository.existsByHorseIdAndOwnerId(id, ownerId))
            throw new ResourceNotFoundException("Không tìm thấy " + label + " được phép chọn: " + id);
        Horse parent = findOptionalParent(id, "Không tìm thấy " + label);
        LocalDate fourYearsAgo = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))).minusYears(4);
        if (parent.getArchivedAt() != null || parent.getGender() != expectedGender
                || !parent.getBirthDate().isBefore(fourYearsAgo)
                || !parent.getBirthDate().isBefore(childBirthDate))
            throw new BusinessException("Hồ sơ " + label + " phải chưa lưu trữ, trên 4 tuổi, đúng giới tính và lớn tuổi hơn ngựa con");
        return parent;
    }

    private Horse findHorse(UUID id) {
        return horseRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + id));
    }

    private UserAccount findTrainer(UUID trainerId) {
        if (trainerId == null) {
            return null;
        }
        return findUserWithRole(trainerId, Role.HEAD_TRAINER, "Huấn luyện viên hiện tại");
    }

    private UserAccount findUserWithRole(UUID userId, Role role, String label) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        label + " không tồn tại: " + userId));
        if (!user.isEnabled() || !user.getRoles().contains(role)) {
            throw new BusinessException(label + " phải là tài khoản " + role + " đang hoạt động");
        }
        return user;
    }

    private void validateParentCycle(Horse horse, UUID sireId, UUID damId) {
        if (horse.getId().equals(sireId) || horse.getId().equals(damId)) {
            throw new BusinessException("Một con ngựa không thể là bố hoặc mẹ của chính nó");
        }
        for (UUID parentId : Arrays.asList(sireId, damId)) {
            if (parentId != null && hasAncestor(parentId, horse.getId(), new HashSet<>())) {
                throw new BusinessException("Quan hệ phả hệ tạo thành vòng lặp");
            }
        }
    }

    private boolean hasAncestor(UUID currentId, UUID targetId, Set<UUID> visited) {
        if (currentId.equals(targetId)) {
            return true;
        }
        if (!visited.add(currentId)) {
            return false;
        }
        Horse current = horseRepository.findDetailById(currentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa cha/mẹ: " + currentId));
        return (current.getSire() != null && hasAncestor(current.getSire().getId(), targetId, visited))
                || (current.getDam() != null && hasAncestor(current.getDam().getId(), targetId, visited));
    }

    private HorseResponse toResponse(Horse horse) {
        return horseMapper.toResponse(
                horse,
                ownershipRepository.findByHorseIdOrderByOwnerUsernameAsc(horse.getId()));
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
