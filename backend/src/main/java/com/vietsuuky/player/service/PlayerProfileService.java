package com.vietsuuky.player.service;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.player.dto.PlayerProfileResponse;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PlayerProfileService {

    private final PlayerProfileRepository playerProfileRepository;

    public PlayerProfileService(PlayerProfileRepository playerProfileRepository) {
        this.playerProfileRepository = playerProfileRepository;
    }

    @Transactional
    public PlayerProfile createForAccount(Account account, String displayName) {
        if (playerProfileRepository.existsByAccount(account)) {
            throw new ApiException("Player profile already exists for this account");
        }

        PlayerProfile profile = new PlayerProfile(account, displayName);
        return playerProfileRepository.save(profile);
    }

    public Optional<PlayerProfile> findByAccount(Account account) {
        return playerProfileRepository.findByAccount(account);
    }

    public PlayerProfile getForAccount(Account account) {
        return playerProfileRepository.findByAccount(account)
                .orElseThrow(() -> new ApiException("Player profile not found"));
    }

    @Transactional(readOnly = true)
    public PlayerProfileResponse getResponseForAccount(UUID accountId) {
        PlayerProfile profile = playerProfileRepository.findWithAccountByAccount_Id(accountId)
                .orElseThrow(() -> new ApiException("Player profile not found"));
        return toResponse(profile);
    }

    public PlayerProfileResponse toResponse(PlayerProfile profile) {
        return new PlayerProfileResponse(
                profile.getId(),
                profile.getAccount().getId(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getAccount().getEmail()
        );
    }

    @Transactional
    public PlayerProfileResponse updateForAccount(UUID accountId, String displayName, String avatarUrl) {
        PlayerProfile profile = playerProfileRepository.findWithAccountByAccount_Id(accountId)
                .orElseThrow(() -> new ApiException("Player profile not found"));
        if (displayName != null && !displayName.isBlank()) {
            profile.setDisplayName(displayName.trim());
        }
        if (avatarUrl != null) {
            profile.setAvatarUrl(avatarUrl.trim().isEmpty() ? null : avatarUrl.trim());
        }
        return toResponse(playerProfileRepository.save(profile));
    }
}
