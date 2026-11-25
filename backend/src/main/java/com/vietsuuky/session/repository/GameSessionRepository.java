package com.vietsuuky.session.repository;

import com.vietsuuky.session.entity.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vietsuuky.session.enums.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {
    List<GameSession> findByPlayerIdOrderByStartedAtDesc(UUID playerId);
    Optional<GameSession> findByIdAndPlayerId(UUID id, UUID playerId);
    boolean existsByPlayerIdAndMissionIdAndStatus(UUID playerId, UUID missionId, SessionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select gs from GameSession gs
            where gs.id = :id and gs.player.id = :playerId
            """)
    Optional<GameSession> findForUpdateByIdAndPlayerId(@Param("id") UUID id,
                                                       @Param("playerId") UUID playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select gs from GameSession gs
            where gs.status = :status
              and (gs.lastActivityAt <= :inactivityCutoff
               or gs.startedAt <= :lifetimeCutoff)
            """)
    List<GameSession> findOverdueActiveForUpdate(@Param("status") SessionStatus status,
                                                  @Param("inactivityCutoff") java.time.Instant inactivityCutoff,
                                                  @Param("lifetimeCutoff") java.time.Instant lifetimeCutoff,
                                                  Pageable pageable);
}
