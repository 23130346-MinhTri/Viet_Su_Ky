package com.vietsuuky.mission.entity;

import com.vietsuuky.mission.enums.MissionStatus;
import com.vietsuuky.mission.enums.MissionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "missions", schema = "app")
@Getter
@Setter
@NoArgsConstructor
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    @NotBlank
    @Size(max = 100)
    private String code;

    @Column(name = "title", nullable = false, length = 150)
    @NotBlank
    @Size(max = 150)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "chapter", nullable = false, length = 100)
    @NotBlank
    private String chapter = "CHAPTER_1";

    @Column(name = "mission_order", nullable = false)
    @NotNull
    private Integer missionOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_type", nullable = false, length = 30)
    @NotNull
    private MissionType missionType = MissionType.QUIZ;

    @Column(name = "unlock_condition", length = 255)
    private String unlockCondition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_mission_id")
    private Mission prerequisiteMission;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints = 0;

    @Column(name = "reward_code", length = 100)
    private String rewardCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MissionStatus status = MissionStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Mission(String code, String title, String chapter, Integer missionOrder, MissionType missionType, int rewardPoints) {
        this.code = code;
        this.title = title;
        this.chapter = chapter;
        this.missionOrder = missionOrder;
        this.missionType = missionType;
        this.rewardPoints = rewardPoints;
    }
}
