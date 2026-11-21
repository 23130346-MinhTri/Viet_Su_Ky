package com.vietsuuky.progress.repository;

import com.vietsuuky.progress.entity.MissionProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissionProgressRepository extends JpaRepository<MissionProgress, UUID> {
    List<MissionProgress> findByPlayerIdOrderByMission_MissionOrderAsc(UUID playerId);
    Optional<MissionProgress> findByPlayerIdAndMissionId(UUID playerId, UUID missionId);
    boolean existsByPlayerIdAndMissionId(UUID playerId, UUID missionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mp from MissionProgress mp where mp.player.id = :playerId and mp.mission.id = :missionId")
    Optional<MissionProgress> findForUpdateByPlayerIdAndMissionId(@Param("playerId") UUID playerId,
                                                                 @Param("missionId") UUID missionId);
}
