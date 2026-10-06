package com.racehorse.horse.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.horse.repository.HorseRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HorseAccessService {

    private final HorseRepository horseRepository;
    private final HorseOwnershipRepository ownershipRepository;

    public HorseAccessService(
            HorseRepository horseRepository,
            HorseOwnershipRepository ownershipRepository) {
        this.horseRepository = horseRepository;
        this.ownershipRepository = ownershipRepository;
    }

    public Horse requireReadable(UUID horseId, UUID userId, boolean privileged) {
        Horse horse = requireExisting(horseId);
        if (!privileged && (horse.getArchivedAt() != null
                || !ownershipRepository.existsByHorseIdAndOwnerId(horseId, userId))) {
            throw new ResourceNotFoundException("Không tìm thấy ngựa được phép truy cập: " + horseId);
        }
        return horse;
    }

    public Horse requireActive(UUID horseId) {
        Horse horse = requireExisting(horseId);
        if (horse.getArchivedAt() != null) {
            throw new ResourceNotFoundException("Hồ sơ ngựa đã được lưu trữ: " + horseId);
        }
        return horse;
    }

    public Horse requireTrainingAccess(UUID horseId, UUID userId,
            boolean manager, boolean trainer, boolean owner) {
        Horse horse = requireActive(horseId);
        if (manager) {
            return horse;
        }
        if (trainer && horse.getCurrentTrainer() != null
                && horse.getCurrentTrainer().getId().equals(userId)) {
            return horse;
        }
        if (owner && ownershipRepository.existsByHorseIdAndOwnerId(horseId, userId)) {
            return horse;
        }
        throw new ResourceNotFoundException("Không tìm thấy ngựa được phép truy cập: " + horseId);
    }

    public Horse requireServiceEligible(UUID horseId) {
        Horse horse = requireActive(horseId);
        boolean isPaid = "PAID".equalsIgnoreCase(horse.getPaymentStatus());
        boolean isOverridden = Boolean.TRUE.equals(horse.getServiceOverride());
        if (!isPaid && !isOverridden) {
            throw new BusinessException("Ngựa " + horse.getName() + " chưa thanh toán phí dịch vụ chăm sóc và chưa được Club Manager duyệt bảo lãnh dịch vụ");
        }
        return horse;
    }

    public Horse requireOwned(UUID horseId, UUID ownerId) {
        Horse horse = requireExisting(horseId);
        if (!ownershipRepository.existsByHorseIdAndOwnerId(horseId, ownerId)) {
            throw new ResourceNotFoundException("Không tìm thấy ngựa được phép truy cập: " + horseId);
        }
        return horse;
    }

    public Horse requireExisting(UUID horseId) {
        return findExisting(horseId);
    }

    private Horse findExisting(UUID horseId) {
        return horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));
    }
}
