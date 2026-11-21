package com.vietsuuky.save.controller;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.auth.service.AuthenticationService;
import com.vietsuuky.common.response.ApiResponse;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import com.vietsuuky.save.dto.GameSaveRequest;
import com.vietsuuky.save.dto.GameSaveResponse;
import com.vietsuuky.save.service.GameSaveService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GameSaveController {

    private final GameSaveService gameSaveService;
    private final AuthenticationService authenticationService;
    private final PlayerProfileService playerProfileService;

    public GameSaveController(GameSaveService gameSaveService,
                             AuthenticationService authenticationService,
                             PlayerProfileService playerProfileService) {
        this.gameSaveService = gameSaveService;
        this.authenticationService = authenticationService;
        this.playerProfileService = playerProfileService;
    }

    @GetMapping("/players/me/game-saves")
    public ResponseEntity<ApiResponse<List<GameSaveResponse>>> listSaves(@AuthenticationPrincipal User user) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        return ResponseEntity.ok(ApiResponse.success("Game saves retrieved", gameSaveService.getSavesForPlayer(player.getId())));
    }

    @GetMapping("/players/me/game-saves/{saveId}")
    public ResponseEntity<ApiResponse<GameSaveResponse>> getSave(@AuthenticationPrincipal User user,
                                                               @PathVariable UUID saveId) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        return ResponseEntity.ok(ApiResponse.success("Game save retrieved", gameSaveService.getSaveForPlayer(player.getId(), saveId)));
    }

    @PutMapping("/players/me/game-saves/{saveId}")
    public ResponseEntity<ApiResponse<GameSaveResponse>> upsertSave(@AuthenticationPrincipal User user,
                                                                   @PathVariable UUID saveId,
                                                                   @Valid @RequestBody GameSaveRequest request) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        GameSaveResponse response = gameSaveService.upsertSave(player.getId(), saveId, request);
        return ResponseEntity.ok(ApiResponse.success("Game save saved", response));
    }
}
