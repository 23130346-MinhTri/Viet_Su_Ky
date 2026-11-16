package com.vietsuuky.auth.service;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.service.AccountService;
import com.vietsuuky.auth.dto.AuthResponse;
import com.vietsuuky.auth.dto.LoginRequest;
import com.vietsuuky.auth.dto.RegisterRequest;
import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthenticationService {

    private final AccountService accountService;
    private final PlayerProfileService playerProfileService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(AccountService accountService,
                                 PlayerProfileService playerProfileService,
                                 PasswordEncoder passwordEncoder,
                                 JwtService jwtService) {
        this.accountService = accountService;
        this.playerProfileService = playerProfileService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public Account register(RegisterRequest request) {
        String normalizedEmail = AccountService.normalizeEmail(request.email());
        if (accountService.emailExists(normalizedEmail)) {
            throw new ApiException("Email already exists");
        }

        Account account = accountService.createAccount(normalizedEmail, request.password());
        String sanitizedDisplayName = request.displayName().trim();
        playerProfileService.createForAccount(account, sanitizedDisplayName);
        return account;
    }

    public AuthResponse login(LoginRequest request) {
        Account account = accountService.findByEmail(request.email())
                .orElseThrow(() -> new ApiException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new ApiException("Invalid email or password");
        }

        accountService.ensureAccountActive(account);
        String token = jwtService.generateToken(account.getId());

        PlayerProfile profile = playerProfileService.getForAccount(account);
        return new AuthResponse(
                token,
                "Bearer",
                900,
                new AuthResponse.AccountSummary(account.getId().toString(), account.getEmail(), profile.getDisplayName())
        );
    }

    public Account getCurrentAccount(UUID accountId) {
        return accountService.getAccountOrThrow(accountId);
    }
}
