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
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.entity.HorseOwnership;
import com.racehorse.horse.entity.HorseStatus;
import com.racehorse.horse.mapper.HorseMapper;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.horse.repository.HorseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
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

    public HorseService(
            HorseRepository horseRepository,
            HorseOwnershipRepository ownershipRepository,
            UserAccountRepository userRepository,
            HorseMapper horseMapper,
            Clock clock) {
        this.horseRepository = horseRepository;
        this.ownershipRepository = ownershipRepository;
        this.userRepository = userRepository;
        this.horseMapper = horseMapper;
        this.clock = clock;
    }

    @Transactional
    public HorseResponse create(CreateHorseRequest request) {
        validateUniqueIdentifiers(request);
        validateDistinctParents(request.sireId(), request.damId());

        Horse sire = findOptionalParent(request.sireId(), "Không tìm thấy ngựa bố");
        Horse dam = findOptionalParent(request.damId(), "Không tìm thấy ngựa mẹ");

        Horse horse = horseMapper.toEntity(request);
        horse.assignParents(sire, dam);
        horse.assignCurrentTrainer(findTrainer(request.currentTrainerId()));
        return toResponse(horseRepository.save(horse));
    }

    @Transactional(readOnly = true)
    public HorseResponse getByIdForUser(UUID id, UUID userId, boolean privileged) {
        Horse horse = findHorse(id);
        if (!privileged && (horse.getArchivedAt() != null
                || !ownershipRepository.existsByHorseIdAndOwnerId(id, userId))) {
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
                ? horseRepository.searchPaidOnly(normalizedQuery, status, pageable)
                : horseRepository.search(normalizedQuery, status, pageable))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<HorseResponse> getEligibleForMedical() {
        return horseRepository.findEligibleForMedical().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<HorseResponse> findOwnedBy(UUID ownerId, Pageable pageable) {
        return horseRepository.findOwnedBy(ownerId, pageable).map(this::toResponse);
    }

    @Transactional
    public HorseResponse update(UUID id, UpdateHorseRequest request) {
        Horse horse = findHorse(id);
        validateDistinctParents(request.sireId(), request.damId());
        validateParentCycle(horse, request.sireId(), request.damId());

        String chip = normalizeNullable(request.microchipPassportNumber());
        if (chip != null
                && horseRepository.existsByMicrochipPassportNumberIgnoreCaseAndIdNot(chip, id)) {
            throw new BusinessException("Số chip/hộ chiếu đã tồn tại: " + chip);
        }

        Horse sire = findOptionalParent(request.sireId(), "Không tìm thấy ngựa bố");
        Horse dam = findOptionalParent(request.damId(), "Không tìm thấy ngựa mẹ");
        horse.update(
                request.name().trim(),
                chip,
                request.birthDate(),
                request.gender(),
                request.colorMarkings().trim(),
                request.breed().trim(),
                request.stableLocation().trim(),
                request.status());
        horse.assignParents(sire, dam);
        horse.assignCurrentTrainer(findTrainer(request.currentTrainerId()));
        return toResponse(horse);
    }

    @Transactional
    public HorseResponse replaceOwnership(UUID horseId, ReplaceOwnershipRequest request) {
        if (!request.confirmed()) {
            throw new BusinessException("Phải xác nhận trước khi thay đổi tỷ lệ sở hữu");
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

    private Horse findOptionalParent(UUID id, String errorMessage) {
        if (id == null) {
            return null;
        }
        return horseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(errorMessage + ": " + id));
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
}
