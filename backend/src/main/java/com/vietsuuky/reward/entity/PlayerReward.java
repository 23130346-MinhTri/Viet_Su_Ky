package com.vietsuuky.reward.entity;

import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.reward.enums.RewardType;
import com.vietsuuky.session.entity.GameSession;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "player_rewards", schema = "app",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"player_id", "mission_id", "reward_source", "reward_type"}),
                @UniqueConstraint(columnNames = {"player_id", "reward_source", "source_reference", "reward_type"}),
                @UniqueConstraint(columnNames = {"player_id", "session_id", "reward_source", "reward_type"})
        })
@Getter
@Setter
@NoArgsConstructor
public class PlayerReward {
    private static final Set<String> REFERENCE_REQUIRED_REWARD_SOURCES = Set.of("ACHIEVEMENT", "REPLAY", "EVENT", "OPTIONAL_MISSION");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerProfile player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private GameSession session;

    @Column(name = "reward_source", nullable = false, length = 50)
    private String rewardSource = "MISSION_COMPLETION";

    @Column(name = "source_reference", length = 128)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 30)
    private RewardType rewardType = RewardType.MISSION_COMPLETE;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints = 0;

    @Column(name = "reward_code", length = 100)
    private String rewardCode;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "CLAIMED";

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    @PreUpdate
    public void validateRewardPolicy() {
        if (rewardSource == null || rewardSource.isBlank()) {
            throw new IllegalArgumentException("rewardSource is required");
        }
        if (REFERENCE_REQUIRED_REWARD_SOURCES.contains(rewardSource)
                && (sourceReference == null || sourceReference.isBlank())) {
            throw new IllegalArgumentException(rewardSource + " rewards require a non-blank source_reference");
        }
    }

    public PlayerReward(PlayerProfile player, Mission mission, RewardType rewardType, int rewardPoints, String rewardCode) {
        this.player = player;
        this.mission = mission;
        this.rewardType = rewardType;
        this.rewardPoints = rewardPoints;
        this.rewardCode = rewardCode;
        this.status = "CLAIMED";
        this.claimedAt = Instant.now();
        this.rewardSource = "MISSION_COMPLETION";
    }
}
