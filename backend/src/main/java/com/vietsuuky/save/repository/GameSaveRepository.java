package com.vietsuuky.save.repository;

import com.vietsuuky.save.entity.GameSave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameSaveRepository extends JpaRepository<GameSave, UUID> {
    List<GameSave> findByPlayerIdOrderByUpdatedAtDesc(UUID playerId);
    Optional<GameSave> findByIdAndPlayerId(UUID id, UUID playerId);
    Optional<GameSave> findByPlayerIdAndSaveName(UUID playerId, String saveName);
}
