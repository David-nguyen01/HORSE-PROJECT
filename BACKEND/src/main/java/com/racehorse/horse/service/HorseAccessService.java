package com.racehorse.horse.service;

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

    private Horse requireExisting(UUID horseId) {
        return horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));
    }
}
