package com.vietsuuky.session.entity;

import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.session.enums.SessionStatus;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_sessions", schema = "app")
@Getter
@Setter
@NoArgsConstructor
public class GameSession {

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

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "event_count", nullable = false)
    private int eventCount = 0;

    @Column(name = "final_score", nullable = false)
    private int finalScore = 0;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GameSession(PlayerProfile player, Mission mission) {
        this.player = player;
        this.mission = mission;
        this.startedAt = Instant.now();
        this.status = SessionStatus.ACTIVE;
    }
}
