package com.vietsuuky.session.service;

import java.time.Duration;
import java.time.Instant;

public final class GameSessionExpirationPolicy {

    public static final Duration INACTIVITY_LIMIT = Duration.ofMinutes(30);
    public static final Duration ABSOLUTE_LIFETIME_LIMIT = Duration.ofMinutes(120);

    private GameSessionExpirationPolicy() {
    }

    public static boolean isExpired(Instant now, Instant startedAt, Instant lastActivityAt) {
        if (now == null || startedAt == null || lastActivityAt == null) {
            return false;
        }

        Duration inactivity = Duration.between(lastActivityAt, now);
        Duration lifetime = Duration.between(startedAt, now);
        return inactivity.compareTo(INACTIVITY_LIMIT) >= 0
                || lifetime.compareTo(ABSOLUTE_LIFETIME_LIMIT) >= 0;
    }
}
