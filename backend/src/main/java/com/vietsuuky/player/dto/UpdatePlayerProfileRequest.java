package com.vietsuuky.player.dto;

import jakarta.validation.constraints.Size;

public record UpdatePlayerProfileRequest(
        @Size(min = 2, max = 50, message = "Display name must be between 2 and 50 characters")
        String displayName,

        @Size(max = 2048, message = "Avatar URL is too long")
        String avatarUrl
) {}
