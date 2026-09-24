package com.racehorse.racing.service;

import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.racing.dto.RaceHistoryResponse;
import com.racehorse.racing.dto.RaceResultResponse;
import com.racehorse.racing.dto.UpsertRaceResultRequest;
import com.racehorse.racing.entity.RaceResult;
import com.racehorse.racing.repository.RaceResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RaceResultService {

    private final RaceResultRepository repository;
    private final HorseAccessService horseAccessService;

    public RaceResultService(RaceResultRepository repository, HorseAccessService horseAccessService) {
        this.repository = repository;
        this.horseAccessService = horseAccessService;
    }

    @Transactional
    public RaceResultResponse create(UUID horseId, UpsertRaceResultRequest request) {
        Horse horse = horseAccessService.requireActive(horseId);
        return toResponse(repository.save(new RaceResult(
                horse,
                request.raceDate(),
                trim(request.raceName()),
                trim(request.racecourse()),
                trim(request.distance()),
                request.finishPosition(),
                trim(request.finishTime()),
                trim(request.jockey()),
                trimNullable(request.trackCondition()),
                trim(request.prizeMoney()))));
    }

    @Transactional
    public RaceResultResponse update(UUID horseId, UUID resultId, UpsertRaceResultRequest request) {
        horseAccessService.requireActive(horseId);
        RaceResult result = repository.findByIdAndHorseId(resultId, horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kết quả đua: " + resultId));
        result.update(
                request.raceDate(),
                trim(request.raceName()),
                trim(request.racecourse()),
                trim(request.distance()),
                request.finishPosition(),
                trim(request.finishTime()),
                trim(request.jockey()),
                trimNullable(request.trackCondition()),
                trim(request.prizeMoney()));
        return toResponse(result);
    }

    @Transactional
    public void delete(UUID horseId, UUID resultId) {
        horseAccessService.requireActive(horseId);
        RaceResult result = repository.findByIdAndHorseId(resultId, horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kết quả đua: " + resultId));
        repository.delete(result);
    }

    @Transactional(readOnly = true)
    public RaceHistoryResponse history(UUID horseId, UUID userId, boolean privileged) {
        horseAccessService.requireReadable(horseId, userId, privileged);
        List<RaceResultResponse> results = repository
                .findByHorseIdOrderByRaceDateDescCreatedAtDesc(horseId)
                .stream()
                .map(this::toResponse)
                .toList();
        long wins = results.stream().filter(result -> result.finishPosition() == 1).count();
        return new RaceHistoryResponse(horseId, results.size(), wins, results);
    }

    private RaceResultResponse toResponse(RaceResult result) {
        return new RaceResultResponse(
                result.getId(),
                result.getHorse().getId(),
                result.getRaceDate(),
                result.getRaceName(),
                result.getRacecourse(),
                result.getDistance(),
                result.getFinishPosition(),
                result.getFinishTime(),
                result.getJockey(),
                result.getTrackCondition(),
                result.getPrizeMoney(),
                result.getCreatedAt(),
                result.getUpdatedAt());
    }

    private String trim(String value) {
        return value.trim();
    }

    private String trimNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
