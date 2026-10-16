package com.racehorse.training.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.service.HorseAccessService;
import com.racehorse.training.dto.CreateRacetrackRequest;
import com.racehorse.training.dto.CreateTrialRunBookingRequest;
import com.racehorse.training.dto.RacetrackResponse;
import com.racehorse.training.dto.TrackLaneAvailabilityResponse;
import com.racehorse.training.dto.TrialRunBookingResponse;
import com.racehorse.training.entity.Racetrack;
import com.racehorse.training.entity.RacetrackLane;
import com.racehorse.training.entity.TrainingSchedule;
import com.racehorse.training.entity.TrialRunBooking;
import com.racehorse.training.repository.RacetrackLaneRepository;
import com.racehorse.training.repository.RacetrackRepository;
import com.racehorse.training.repository.TrialRunBookingRepository;
import com.racehorse.training.repository.TrainingScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RacetrackService {
    private static final LocalTime OPENS_AT = LocalTime.of(5, 0);
    private static final LocalTime CLOSES_AT = LocalTime.of(11, 0);

    private final RacetrackRepository trackRepository;
    private final RacetrackLaneRepository laneRepository;
    private final TrialRunBookingRepository bookingRepository;
    private final TrainingScheduleRepository scheduleRepository;
    private final HorseAccessService horseAccessService;
    private final ZoneId clubZone;

    public RacetrackService(RacetrackRepository trackRepository, RacetrackLaneRepository laneRepository,
            TrialRunBookingRepository bookingRepository, TrainingScheduleRepository scheduleRepository,
            HorseAccessService horseAccessService,
            @Value("${app.club-time-zone:Asia/Ho_Chi_Minh}") String clubTimeZone) {
        this.trackRepository = trackRepository;
        this.laneRepository = laneRepository;
        this.bookingRepository = bookingRepository;
        this.scheduleRepository = scheduleRepository;
        this.horseAccessService = horseAccessService;
        this.clubZone = ZoneId.of(clubTimeZone);
    }

    @Transactional
    public RacetrackResponse create(CreateRacetrackRequest request) {
        if (request.laneCount() != 4) throw new InvalidInputException("Mỗi sân tập phải có đúng 4 làn chạy");
        String name = request.name().trim();
        if (trackRepository.existsByNameIgnoreCase(name)) throw new BusinessException("Tên đường đua đã tồn tại");
        Racetrack track = trackRepository.save(new Racetrack(name, trimToNull(request.location())));
        List<RacetrackLane> lanes = new ArrayList<>();
        for (int number = 1; number <= 4; number++) lanes.add(new RacetrackLane(track, number));
        laneRepository.saveAll(lanes);
        return toResponse(track, lanes);
    }

    @Transactional(readOnly = true)
    public List<RacetrackResponse> list() {
        return trackRepository.findAllByOrderByNameAsc().stream()
                .map(track -> toResponse(track, laneRepository.findByRacetrackIdOrderByLaneNumberAsc(track.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrackLaneAvailabilityResponse> availability(UUID racetrackId, LocalDate date) {
        Racetrack track = requireTrack(racetrackId);
        if (!track.isActive()) return List.of();
        Instant start = date.atTime(OPENS_AT).atZone(clubZone).toInstant();
        Instant end = date.atTime(CLOSES_AT).atZone(clubZone).toInstant();
        List<TrialRunBooking> occupied = bookingRepository
                .findByLaneRacetrackIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
                        racetrackId, end, start);
        java.util.Set<UUID> occupiedLaneIds = occupied.stream().map(value -> value.getLane().getId())
                .collect(java.util.stream.Collectors.toSet());
        return laneRepository.findByRacetrackIdOrderByLaneNumberAsc(racetrackId).stream()
                .filter(RacetrackLane::isActive)
                .filter(lane -> lane.getLaneNumber() >= 1 && lane.getLaneNumber() <= 4)
                .filter(lane -> !occupiedLaneIds.contains(lane.getId()))
                .map(lane -> new TrackLaneAvailabilityResponse(lane.getId(), lane.getLaneNumber(), date))
                .toList();
    }

    @Transactional
    public TrialRunBookingResponse book(CreateTrialRunBookingRequest request, UUID actorId,
            boolean manager, boolean trainer) {
        RacetrackLane lane = laneRepository.findDetailByIdForUpdate(request.racetrackLaneId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy làn chạy"));
        if (!lane.isActive() || lane.getLaneNumber() < 1 || lane.getLaneNumber() > 4
                || !lane.getRacetrack().isActive()) {
            throw new BusinessException("Sân hoặc làn chạy đang ngừng hoạt động");
        }
        TrainingSchedule schedule = scheduleRepository.findDetailById(request.trainingScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch huấn luyện"));
        horseAccessService.requireTrainingAccess(schedule.getHorse().getId(), actorId, manager, trainer, false);
        if (!schedule.getRacetrack().getId().equals(lane.getRacetrack().getId())) {
            throw new InvalidInputException("Làn chạy phải thuộc sân đã chọn cho buổi tập");
        }
        if (schedule.getCancelledAt() != null) throw new BusinessException("Không thể đặt lượt chạy cho lịch đã hủy");
        LocalDate scheduleDate = schedule.getScheduledStart().atZone(clubZone).toLocalDate();
        if (!scheduleDate.equals(request.date())) throw new InvalidInputException("Ngày đặt làn phải trùng với ngày buổi tập");
        if (!schedule.getScheduledStart().atZone(clubZone).toLocalTime().equals(OPENS_AT)
                || !schedule.getScheduledEnd().atZone(clubZone).toLocalTime().equals(CLOSES_AT)) {
            throw new InvalidInputException("Buổi tập phải trong khung cố định 05:00 - 11:00");
        }
        if (bookingRepository.existsByTrainingScheduleId(schedule.getId())) {
            throw new BusinessException("Lịch huấn luyện này đã có làn chạy");
        }
        Instant start = schedule.getScheduledStart();
        Instant end = schedule.getScheduledEnd();
        return toResponse(bookingRepository.saveAndFlush(new TrialRunBooking(schedule, lane, start, end)));
    }

    @Transactional(readOnly = true)
    public List<TrialRunBookingResponse> bookings(UUID racetrackId, UUID horseId, LocalDate from, LocalDate to,
            boolean includeCancelled, UUID actorId, boolean manager, boolean trainer) {
        if (from != null && to != null && to.isBefore(from)) throw new InvalidInputException("Ngày kết thúc không được trước ngày bắt đầu");
        if (horseId != null) horseAccessService.requireTrainingAccess(horseId, actorId, manager, trainer, false);
        Instant start = from == null ? null : from.atStartOfDay(clubZone).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(clubZone).toInstant();
        UUID trainerScope = trainer && !manager ? actorId : null;
        List<TrialRunBookingResponse> results = bookingRepository
                .search(racetrackId, horseId, includeCancelled, trainerScope, start, end).stream()
                .map(this::toResponse).toList();
        return results;
    }

    @Transactional
    public void cancelBooking(UUID id, UUID actorId, boolean manager, boolean trainer) {
        TrialRunBooking booking = bookingRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt chạy thử: " + id));
        horseAccessService.requireTrainingAccess(booking.getTrainingSchedule().getHorse().getId(), actorId,
                manager, trainer, false);
        if (booking.getCancelledAt() != null) throw new BusinessException("Lượt chạy thử đã bị hủy");
        booking.cancel(Instant.now());
    }

    private Racetrack requireTrack(UUID id) {
        return trackRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sân tập: " + id));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RacetrackResponse toResponse(Racetrack track, List<RacetrackLane> lanes) {
        return new RacetrackResponse(track.getId(), track.getName(), track.getLocation(), track.isActive(),
                OPENS_AT, CLOSES_AT, false, 360,
                lanes.stream().filter(lane -> lane.getLaneNumber() >= 1 && lane.getLaneNumber() <= 4)
                        .map(lane -> new RacetrackResponse.Lane(lane.getId(), lane.getLaneNumber(), lane.isActive())).toList());
    }

    private TrialRunBookingResponse toResponse(TrialRunBooking booking) {
        return new TrialRunBookingResponse(booking.getId(), booking.getTrainingSchedule().getId(),
                booking.getTrainingSchedule().getHorse().getId(), booking.getLane().getRacetrack().getId(),
                booking.getLane().getRacetrack().getName(), booking.getLane().getId(), booking.getLane().getLaneNumber(),
                booking.getBookedStart(), booking.getBookedEnd(), booking.getCancelledAt());
    }
}
