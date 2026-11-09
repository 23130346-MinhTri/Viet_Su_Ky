package com.vietsuuky.player.controller;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.auth.service.AuthenticationService;
import com.vietsuuky.common.response.ApiResponse;
import com.vietsuuky.player.dto.PlayerProfileResponse;
import com.vietsuuky.player.dto.UpdatePlayerProfileRequest;
import com.vietsuuky.player.service.PlayerProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final AuthenticationService authenticationService;
    private final PlayerProfileService playerProfileService;

    public PlayerController(AuthenticationService authenticationService, PlayerProfileService playerProfileService) {
        this.authenticationService = authenticationService;
        this.playerProfileService = playerProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PlayerProfileResponse>> me(@AuthenticationPrincipal User user) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfileResponse profile = playerProfileService.getResponseForAccount(account.getId());
        return ResponseEntity.ok(ApiResponse.success("Player profile retrieved", profile));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<PlayerProfileResponse>> updateMe(@AuthenticationPrincipal User user,
                                                                      @Valid @RequestBody UpdatePlayerProfileRequest request) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfileResponse updated = playerProfileService.updateForAccount(
                account.getId(),
                request.displayName(),
                request.avatarUrl()
        );
        return ResponseEntity.ok(ApiResponse.success("Player profile updated", updated));
    }
}
