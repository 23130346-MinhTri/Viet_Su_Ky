package com.vietsuuky.player.dto;

import java.util.UUID;

public record PlayerProfileResponse(
        UUID id,
        UUID accountId,
        String displayName,
        String avatarUrl,
        String email
) {}
