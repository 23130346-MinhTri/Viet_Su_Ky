package com.vietsuuky.progress.entity;

import com.vietsuuky.player.entity.PlayerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_progress", schema = "app")
@Getter
@Setter
@NoArgsConstructor
public class PlayerProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, unique = true)
    private PlayerProfile player;

    @Column(name = "chapter", nullable = false, length = 100)
    private String chapter = "CHAPTER_1";

    @Column(name = "total_missions_completed", nullable = false)
    private int totalMissionsCompleted = 0;

    @Column(name = "total_points", nullable = false)
    private int totalPoints = 0;

    @Column(name = "unlocked_content", nullable = false, columnDefinition = "TEXT")
    private String unlockedContent = "[]";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlayerProgress(PlayerProfile player) {
        this.player = player;
    }
}
