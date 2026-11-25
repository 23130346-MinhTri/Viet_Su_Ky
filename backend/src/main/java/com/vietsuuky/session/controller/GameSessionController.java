package com.vietsuuky.session.controller;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.auth.service.AuthenticationService;
import com.vietsuuky.common.response.ApiResponse;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import com.vietsuuky.session.dto.CompleteGameSessionRequest;
import com.vietsuuky.session.dto.CreateGameSessionRequest;
import com.vietsuuky.session.dto.GameSessionEventRequest;
import com.vietsuuky.session.dto.GameSessionResponse;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.service.GameSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GameSessionController {

    private final GameSessionService gameSessionService;
    private final AuthenticationService authenticationService;
    private final PlayerProfileService playerProfileService;

    public GameSessionController(GameSessionService gameSessionService,
                                AuthenticationService authenticationService,
                                PlayerProfileService playerProfileService) {
        this.gameSessionService = gameSessionService;
        this.authenticationService = authenticationService;
        this.playerProfileService = playerProfileService;
    }

    @PostMapping("/game-sessions")
    public ResponseEntity<ApiResponse<GameSessionResponse>> createSession(@AuthenticationPrincipal User user,
                                                                         @Valid @RequestBody CreateGameSessionRequest request) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        GameSession session = gameSessionService.createSession(player.getId(), request.missionId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Game session created", gameSessionService.toResponse(session)));
    }

    @GetMapping("/game-sessions/{sessionId}")
    public ResponseEntity<ApiResponse<GameSessionResponse>> getSession(@AuthenticationPrincipal User user,
                                                                     @PathVariable UUID sessionId) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        GameSession session = gameSessionService.getSessionForPlayer(player.getId(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Game session retrieved", gameSessionService.toResponse(session)));
    }

    @PostMapping("/game-sessions/{sessionId}/events")
    public ResponseEntity<ApiResponse<GameSessionResponse>> recordEvent(@AuthenticationPrincipal User user,
                                                                       @PathVariable UUID sessionId,
                                                                       @Valid @RequestBody GameSessionEventRequest request) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        GameSession session = gameSessionService.recordEvent(player.getId(), sessionId, request);
        return ResponseEntity.ok(ApiResponse.success("Game event recorded", gameSessionService.toResponse(session)));
    }

    @PostMapping("/game-sessions/{sessionId}/complete")
    public ResponseEntity<ApiResponse<GameSessionResponse>> completeSession(@AuthenticationPrincipal User user,
                                                                            @PathVariable UUID sessionId,
                                                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                            @RequestBody CompleteGameSessionRequest request) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        GameSessionResponse response = gameSessionService.completeSession(
                player.getId(), sessionId, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Game session completed", response));
    }

    @PostMapping("/game-sessions/{sessionId}/abandon")
    public ResponseEntity<ApiResponse<GameSessionResponse>> abandonSession(@AuthenticationPrincipal User user,
                                                                         @PathVariable UUID sessionId) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        GameSession session = gameSessionService.abandonSession(player.getId(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Game session abandoned", gameSessionService.toResponse(session)));
    }
}
