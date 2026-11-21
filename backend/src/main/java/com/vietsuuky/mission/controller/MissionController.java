package com.vietsuuky.mission.controller;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.auth.service.AuthenticationService;
import com.vietsuuky.common.response.ApiResponse;
import com.vietsuuky.mission.dto.MissionAvailabilityResponse;
import com.vietsuuky.mission.dto.MissionResponse;
import com.vietsuuky.mission.service.MissionService;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MissionController {

    private final MissionService missionService;
    private final AuthenticationService authenticationService;
    private final PlayerProfileService playerProfileService;

    public MissionController(MissionService missionService,
                            AuthenticationService authenticationService,
                            PlayerProfileService playerProfileService) {
        this.missionService = missionService;
        this.authenticationService = authenticationService;
        this.playerProfileService = playerProfileService;
    }

    @GetMapping("/missions")
    public ResponseEntity<ApiResponse<List<MissionResponse>>> getMissions(@AuthenticationPrincipal User user) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        return ResponseEntity.ok(ApiResponse.success("Missions retrieved", missionService.getMissionResponsesForPlayer(player.getId())));
    }

    @GetMapping("/missions/{missionId}")
    public ResponseEntity<ApiResponse<MissionResponse>> getMission(@PathVariable UUID missionId) {
        return ResponseEntity.ok(ApiResponse.success("Mission retrieved", missionService.getMissionResponse(missionId)));
    }

    @GetMapping("/missions/{missionId}/availability")
    public ResponseEntity<ApiResponse<MissionAvailabilityResponse>> getMissionAvailability(@AuthenticationPrincipal User user,
                                                                                         @PathVariable UUID missionId) {
        Account account = authenticationService.getCurrentAccount(UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        MissionAvailabilityResponse availability = missionService.getAvailabilityForPlayer(player.getId(), missionId);
        return ResponseEntity.ok(ApiResponse.success("Mission availability retrieved", availability));
    }
}
