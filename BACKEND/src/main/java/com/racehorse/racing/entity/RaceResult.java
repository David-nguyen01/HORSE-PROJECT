package com.racehorse.racing.entity;

import com.racehorse.common.persistence.BaseEntity;
import com.racehorse.horse.entity.Horse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDate;

@Entity
@Table(name = "race_results")
public class RaceResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @Column(name = "race_date", nullable = false)
    private LocalDate raceDate;

    @Nationalized
    @Column(name = "race_name", nullable = false, length = 200)
    private String raceName;

    @Nationalized
    @Column(name = "racecourse", nullable = false, length = 200)
    private String racecourse;

    @Column(name = "distance", nullable = false, length = 50)
    private String distance;

    @Column(name = "finish_position", nullable = false)
    private int finishPosition;

    @Column(name = "finish_time", nullable = false, length = 50)
    private String finishTime;

    @Nationalized
    @Column(name = "jockey", nullable = false, length = 150)
    private String jockey;

    @Nationalized
    @Column(name = "track_condition", length = 100)
    private String trackCondition;

    @Column(name = "prize_money", nullable = false, length = 100)
    private String prizeMoney;

    protected RaceResult() {
    }

    public RaceResult(
            Horse horse,
            LocalDate raceDate,
            String raceName,
            String racecourse,
            String distance,
            int finishPosition,
            String finishTime,
            String jockey,
            String trackCondition,
            String prizeMoney) {
        this.horse = horse;
        update(raceDate, raceName, racecourse, distance, finishPosition, finishTime, jockey, trackCondition, prizeMoney);
    }

    public void update(
            LocalDate raceDate,
            String raceName,
            String racecourse,
            String distance,
            int finishPosition,
            String finishTime,
            String jockey,
            String trackCondition,
            String prizeMoney) {
        this.raceDate = raceDate;
        this.raceName = raceName;
        this.racecourse = racecourse;
        this.distance = distance;
        this.finishPosition = finishPosition;
        this.finishTime = finishTime;
        this.jockey = jockey;
        this.trackCondition = trackCondition;
        this.prizeMoney = prizeMoney;
    }

    public Horse getHorse() { return horse; }
    public LocalDate getRaceDate() { return raceDate; }
    public String getRaceName() { return raceName; }
    public String getRacecourse() { return racecourse; }
    public String getDistance() { return distance; }
    public int getFinishPosition() { return finishPosition; }
    public String getFinishTime() { return finishTime; }
    public String getJockey() { return jockey; }
    public String getTrackCondition() { return trackCondition; }
    public String getPrizeMoney() { return prizeMoney; }
}
