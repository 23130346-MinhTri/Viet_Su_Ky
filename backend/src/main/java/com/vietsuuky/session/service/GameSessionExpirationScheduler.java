package com.vietsuuky.session.service;

import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.enums.SessionStatus;
import com.vietsuuky.session.repository.GameSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class GameSessionExpirationScheduler {

    private final GameSessionRepository gameSessionRepository;
    private final Clock clock;

    public GameSessionExpirationScheduler(GameSessionRepository gameSessionRepository, Clock clock) {
        this.gameSessionRepository = gameSessionRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.session.expiration-sweep-ms:60000}",
            initialDelayString = "${app.session.expiration-sweep-ms:60000}")
    @Transactional
    public void expireOverdueSessions() {
        Instant now = clock.instant();
        Instant inactivityCutoff = now.minus(GameSessionExpirationPolicy.INACTIVITY_LIMIT);
        Instant lifetimeCutoff = now.minus(GameSessionExpirationPolicy.ABSOLUTE_LIFETIME_LIMIT);

        List<GameSession> overdue = gameSessionRepository.findOverdueActiveForUpdate(
                SessionStatus.ACTIVE,
                inactivityCutoff,
                lifetimeCutoff,
                PageRequest.of(0, 100));

        for (GameSession session : overdue) {
            if (session.getStatus() == SessionStatus.ACTIVE
                    && GameSessionExpirationPolicy.isExpired(
                            now, session.getStartedAt(), session.getLastActivityAt())) {
                session.setStatus(SessionStatus.EXPIRED);
                session.setEndedAt(now);
            }
        }
        gameSessionRepository.saveAll(overdue);
        gameSessionRepository.flush();
    }
}
