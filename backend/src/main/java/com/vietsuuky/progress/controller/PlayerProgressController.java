package com.vietsuuky.progress.controller;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.auth.service.AuthenticationService;
import com.vietsuuky.common.response.ApiResponse;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import com.vietsuuky.progress.dto.MissionProgressResponse;
import com.vietsuuky.progress.dto.PlayerProgressResponse;
import com.vietsuuky.progress.service.PlayerProgressService;
import com.vietsuuky.reward.dto.PlayerRewardResponse;
import com.vietsuuky.reward.service.PlayerRewardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PlayerProgressController {

    private final AuthenticationService authenticationService;
    private final PlayerProfileService playerProfileService;
    private final PlayerProgressService playerProgressService;
    private final PlayerRewardService playerRewardService;

    public PlayerProgressController(AuthenticationService authenticationService,
                                   PlayerProfileService playerProfileService,
                                   PlayerProgressService playerProgressService,
                                   PlayerRewardService playerRewardService) {
        this.authenticationService = authenticationService;
        this.playerProfileService = playerProfileService;
        this.playerProgressService = playerProgressService;
        this.playerRewardService = playerRewardService;
    }

    @GetMapping("/players/me/progress")
    public ResponseEntity<ApiResponse<PlayerProgressResponse>> getPlayerProgress(@AuthenticationPrincipal User user) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        return ResponseEntity.ok(ApiResponse.success("Player progress retrieved", playerProgressService.getPlayerProgress(player.getId())));
    }

    @GetMapping("/players/me/progress/missions")
    public ResponseEntity<ApiResponse<List<MissionProgressResponse>>> getMissionProgress(@AuthenticationPrincipal User user) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        return ResponseEntity.ok(ApiResponse.success("Mission progress retrieved", playerProgressService.getMissionProgressResponses(player.getId())));
    }

    @GetMapping("/players/me/rewards")
    public ResponseEntity<ApiResponse<List<PlayerRewardResponse>>> getRewards(@AuthenticationPrincipal User user) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        return ResponseEntity.ok(ApiResponse.success("Player rewards retrieved", playerRewardService.getRewardsForPlayer(player.getId())));
    }
}
