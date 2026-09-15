package com.racehorse.training.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.training.dto.*;
import com.racehorse.training.entity.*;
import com.racehorse.training.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RacetrackService {
    private final RacetrackRepository trackRepository;
    private final RacetrackLaneRepository laneRepository;
    private final TrialRunBookingRepository bookingRepository;
    private final TrainingScheduleRepository scheduleRepository;
    private final ZoneId clubZone;

    public RacetrackService(RacetrackRepository trackRepository,
                            RacetrackLaneRepository laneRepository,
                            TrialRunBookingRepository bookingRepository,
                            TrainingScheduleRepository scheduleRepository,
                            @Value("${app.club-time-zone:Asia/Ho_Chi_Minh}") String clubTimeZone) {
        this.trackRepository = trackRepository;
        this.laneRepository = laneRepository;
        this.bookingRepository = bookingRepository;
        this.scheduleRepository = scheduleRepository;
        this.clubZone = ZoneId.of(clubTimeZone);
    }

    @Transactional
    public RacetrackResponse create(CreateRacetrackRequest request) {
        String name = request.name().trim();
        if (trackRepository.existsByNameIgnoreCase(name)) throw new BusinessException("Tên đường đua đã tồn tại");
        Racetrack track = trackRepository.save(new Racetrack(name, trimToNull(request.location())));
        List<RacetrackLane> lanes = new ArrayList<>();
        for (int number = 1; number <= request.laneCount(); number++) lanes.add(new RacetrackLane(track, number));
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
    public List<TrackSlotResponse> slots(UUID racetrackId, LocalDate date) {
        Racetrack track = requireTrack(racetrackId);
        List<RacetrackLane> lanes = laneRepository.findByRacetrackIdOrderByLaneNumberAsc(racetrackId).stream()
                .filter(RacetrackLane::isActive).toList();
        Instant rangeStart = date.atTime(track.getOpensAt()).atZone(clubZone).toInstant();
        Instant rangeEnd = closingDateTime(track, date).atZone(clubZone).toInstant();
        Map<String, TrialRunBooking> occupied = bookingRepository
                .findByLaneRacetrackIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
                        racetrackId, rangeEnd, rangeStart).stream()
                .collect(Collectors.toMap(value -> key(value.getLane().getId(), value.getBookedStart()), Function.identity()));
        List<TrackSlotResponse> result = new ArrayList<>();
        for (RacetrackLane lane : lanes) {
            LocalDateTime cursor = date.atTime(track.getOpensAt());
            LocalDateTime closing = closingDateTime(track, date);
            while (cursor.isBefore(closing)) {
                LocalDateTime end = cursor.plusMinutes(track.getSlotMinutes());
                Instant startInstant = cursor.atZone(clubZone).toInstant();
                TrialRunBooking booking = occupied.get(key(lane.getId(), startInstant));
                result.add(new TrackSlotResponse(lane.getId(), lane.getLaneNumber(), date, cursor.toLocalTime(),
                        end.toLocalTime(), !end.toLocalDate().equals(date), booking == null,
                        booking == null ? null : booking.getId(),
                        booking == null ? null : booking.getTrainingSchedule().getId()));
                cursor = end;
            }
        }
        return result;
    }

    @Transactional
    public TrialRunBookingResponse book(CreateTrialRunBookingRequest request) {
        RacetrackLane lane = laneRepository.findDetailById(request.racetrackLaneId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy làn đường đua: " + request.racetrackLaneId()));
        Racetrack track = lane.getRacetrack();
        if (!track.isActive() || !lane.isActive()) throw new BusinessException("Đường đua hoặc làn chạy đang ngừng hoạt động");
        validateSlotStart(track, request.startTime());
        TrainingSchedule schedule = scheduleRepository.findDetailById(request.trainingScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch huấn luyện: " + request.trainingScheduleId()));
        if (schedule.getCancelledAt() != null) throw new BusinessException("Không thể đặt lượt chạy cho lịch đã hủy");
        if (bookingRepository.existsByTrainingScheduleId(schedule.getId()))
            throw new BusinessException("Lịch huấn luyện này đã có lượt chạy thử");
        Instant start = request.date().atTime(request.startTime()).atZone(clubZone).toInstant();
        Instant end = start.plus(Duration.ofMinutes(track.getSlotMinutes()));
        if (bookingRepository.existsByLaneIdAndCancelledAtIsNullAndBookedStartLessThanAndBookedEndGreaterThan(
                lane.getId(), end, start)) throw new BusinessException("Làn chạy đã được đặt trong slot này");
        return toResponse(bookingRepository.save(new TrialRunBooking(schedule, lane, start, end)));
    }

    private void validateSlotStart(Racetrack track, LocalTime start) {
        if (start.getMinute() != 0 || start.getSecond() != 0 || start.getNano() != 0)
            throw new BusinessException("Slot phải bắt đầu đúng đầu giờ");
        LocalDate sample = LocalDate.of(2000, 1, 1);
        LocalDateTime opening = sample.atTime(track.getOpensAt());
        LocalDateTime closing = closingDateTime(track, sample);
        LocalDateTime requested = sample.atTime(start);
        if (requested.isBefore(opening) || requested.plusMinutes(track.getSlotMinutes()).isAfter(closing))
            throw new BusinessException("Slot phải nằm trong giờ mở cửa 06:00–24:00");
    }

    private LocalDateTime closingDateTime(Racetrack track, LocalDate date) {
        return (track.isClosesNextDay() ? date.plusDays(1) : date).atTime(track.getClosesAt());
    }
    private Racetrack requireTrack(UUID id) { return trackRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đường đua: " + id)); }
    private String key(UUID laneId, Instant start) { return laneId + "|" + start; }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private RacetrackResponse toResponse(Racetrack track, List<RacetrackLane> lanes) {
        return new RacetrackResponse(track.getId(), track.getName(), track.getLocation(), track.isActive(),
                track.getOpensAt(), track.getClosesAt(), track.isClosesNextDay(), track.getSlotMinutes(),
                lanes.stream().map(lane -> new RacetrackResponse.Lane(lane.getId(), lane.getLaneNumber(), lane.isActive())).toList());
    }
    private TrialRunBookingResponse toResponse(TrialRunBooking value) {
        return new TrialRunBookingResponse(value.getId(), value.getTrainingSchedule().getId(),
                value.getTrainingSchedule().getHorse().getId(), value.getLane().getRacetrack().getId(),
                value.getLane().getRacetrack().getName(), value.getLane().getId(), value.getLane().getLaneNumber(),
                value.getBookedStart(), value.getBookedEnd());
    }
}
