package com.vietsuuky.session;

import com.vietsuuky.session.service.GameSessionExpirationPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionExpirationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Test
    void inactivityBoundaryIsStrictlyLessThanThirtyMinutes() {
        Instant started = NOW.minus(Duration.ofMinutes(10));

        assertFalse(GameSessionExpirationPolicy.isExpired(
                NOW, started, NOW.minus(Duration.ofMinutes(29).plusMillis(999))));
        assertTrue(GameSessionExpirationPolicy.isExpired(
                NOW, started, NOW.minus(Duration.ofMinutes(30))));
        assertTrue(GameSessionExpirationPolicy.isExpired(
                NOW, started, NOW.minus(Duration.ofMinutes(30).plusMillis(1))));
    }

    @Test
    void absoluteLifetimeBoundaryIsStrictlyLessThanTwoHours() {
        Instant lastActivity = NOW.minus(Duration.ofSeconds(1));

        assertFalse(GameSessionExpirationPolicy.isExpired(
                NOW, NOW.minus(Duration.ofMinutes(119).minusMillis(999)), lastActivity));
        assertTrue(GameSessionExpirationPolicy.isExpired(
                NOW, NOW.minus(Duration.ofMinutes(120)), lastActivity));
        assertTrue(GameSessionExpirationPolicy.isExpired(
                NOW, NOW.minus(Duration.ofMinutes(120).plusMillis(1)), lastActivity));
    }

    @Test
    void earlierExpirationConditionTakesEffect() {
        assertTrue(GameSessionExpirationPolicy.isExpired(
                NOW,
                NOW.minus(Duration.ofMinutes(119)),
                NOW.minus(Duration.ofMinutes(30))));
    }

    @Test
    void validEventRefreshKeepsAbsoluteLifetimeCeiling() {
        Instant started = NOW.minus(Duration.ofMinutes(119));
        Instant lastActivity = NOW.minus(Duration.ofMinutes(29));

        assertFalse(GameSessionExpirationPolicy.isExpired(NOW, started, lastActivity));
        assertTrue(GameSessionExpirationPolicy.isExpired(
                NOW.plus(Duration.ofMinutes(1)),
                started,
                NOW));
    }
}
