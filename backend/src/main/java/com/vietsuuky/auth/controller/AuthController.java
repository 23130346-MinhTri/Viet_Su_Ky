package com.vietsuuky.auth.controller;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.auth.dto.AuthResponse;
import com.vietsuuky.auth.dto.LoginRequest;
import com.vietsuuky.auth.dto.RegisterRequest;
import com.vietsuuky.auth.service.AuthenticationService;
import com.vietsuuky.common.response.ApiResponse;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final PlayerProfileService playerProfileService;

    public AuthController(AuthenticationService authenticationService, PlayerProfileService playerProfileService) {
        this.authenticationService = authenticationService;
        this.playerProfileService = playerProfileService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse.AccountSummary>> register(@Valid @RequestBody RegisterRequest request) {
        Account account = authenticationService.register(request);
        PlayerProfile profile = playerProfileService.getForAccount(account);
        AuthResponse.AccountSummary summary = new AuthResponse.AccountSummary(
                account.getId().toString(),
                account.getEmail(),
                profile.getDisplayName()
        );
        return ResponseEntity.ok(ApiResponse.success("Registration successful", summary));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authenticationService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Account>> me(@AuthenticationPrincipal User user) {
        UUID accountId = UUID.fromString(user.getUsername());
        Account account = authenticationService.getCurrentAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success("Account retrieved", account));
    }
}
