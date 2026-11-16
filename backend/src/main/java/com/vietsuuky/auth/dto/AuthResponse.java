package com.vietsuuky.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AccountSummary account
) {
    public record AccountSummary(String id, String email, String displayName) {}
}
