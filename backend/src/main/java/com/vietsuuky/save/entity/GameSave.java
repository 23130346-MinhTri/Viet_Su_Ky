package com.vietsuuky.save.entity;

import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.session.entity.GameSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_saves", schema = "app",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "save_name"}))
@Getter
@Setter
@NoArgsConstructor
public class GameSave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerProfile player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private GameSession session;

    @Column(name = "save_name", nullable = false, length = 100)
    private String saveName;

    @Column(name = "save_data", nullable = false, columnDefinition = "TEXT")
    private String saveData;

    @Column(name = "save_size", nullable = false)
    private int saveSize = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GameSave(PlayerProfile player, String saveName, String saveData) {
        this.player = player;
        this.saveName = saveName;
        this.saveData = saveData;
        this.saveSize = saveData == null ? 0 : saveData.length();
    }
}
