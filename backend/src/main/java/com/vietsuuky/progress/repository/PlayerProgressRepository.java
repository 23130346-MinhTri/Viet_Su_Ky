package com.vietsuuky.progress.repository;

import com.vietsuuky.progress.entity.PlayerProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerProgressRepository extends JpaRepository<PlayerProgress, UUID> {
    Optional<PlayerProgress> findByPlayerId(UUID playerId);
}
