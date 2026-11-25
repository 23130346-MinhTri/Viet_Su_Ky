package com.vietsuuky.progress.repository;

import com.vietsuuky.progress.entity.PlayerProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlayerProgressRepository extends JpaRepository<PlayerProgress, UUID> {
    Optional<PlayerProgress> findByPlayerId(UUID playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pp from PlayerProgress pp where pp.player.id = :playerId")
    Optional<PlayerProgress> findForUpdateByPlayerId(@Param("playerId") UUID playerId);
}
