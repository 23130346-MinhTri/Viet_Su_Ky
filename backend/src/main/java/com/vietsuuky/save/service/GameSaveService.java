package com.vietsuuky.save.service;

import com.vietsuuky.common.exception.ApiException;
import com.vietsuuky.mission.entity.Mission;
import com.vietsuuky.mission.repository.MissionRepository;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import com.vietsuuky.save.dto.GameSaveRequest;
import com.vietsuuky.save.dto.GameSaveResponse;
import com.vietsuuky.save.entity.GameSave;
import com.vietsuuky.save.repository.GameSaveRepository;
import com.vietsuuky.session.entity.GameSession;
import com.vietsuuky.session.repository.GameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GameSaveService {

    private static final int MAX_SAVE_SIZE_BYTES = 1_048_576;

    private final GameSaveRepository gameSaveRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final MissionRepository missionRepository;
    private final GameSessionRepository gameSessionRepository;

    public GameSaveService(GameSaveRepository gameSaveRepository,
                          PlayerProfileRepository playerProfileRepository,
                          MissionRepository missionRepository,
                          GameSessionRepository gameSessionRepository) {
        this.gameSaveRepository = gameSaveRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.missionRepository = missionRepository;
        this.gameSessionRepository = gameSessionRepository;
    }

    public List<GameSaveResponse> getSavesForPlayer(UUID playerId) {
        return gameSaveRepository.findByPlayerIdOrderByUpdatedAtDesc(playerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public GameSaveResponse getSaveForPlayer(UUID playerId, UUID saveId) {
        GameSave save = gameSaveRepository.findByIdAndPlayerId(saveId, playerId)
                .orElseThrow(() -> new ApiException("Save not found or access denied"));
        return toResponse(save);
    }

    @Transactional
    public GameSaveResponse upsertSave(UUID playerId, UUID saveId, GameSaveRequest request) {
        if (request.saveData() == null || request.saveData().isBlank()) {
            throw new ApiException("saveData is required");
        }
        if (request.saveData().length() > MAX_SAVE_SIZE_BYTES) {
            throw new ApiException("saveData exceeds the maximum supported size");
        }

        PlayerProfile player = playerProfileRepository.findById(playerId)
                .orElseThrow(() -> new ApiException("Player not found"));

        GameSave save = gameSaveRepository.findByIdAndPlayerId(saveId, playerId)
                .orElseGet(() -> gameSaveRepository.findByPlayerIdAndSaveName(playerId, request.saveName())
                        .orElseGet(() -> new GameSave(player, request.saveName(), request.saveData())));

        if (request.missionId() != null) {
            Mission mission = missionRepository.findById(request.missionId())
                    .orElseThrow(() -> new ApiException("Mission not found"));
            save.setMission(mission);
        }

        if (request.sessionId() != null) {
            GameSession session = gameSessionRepository.findByIdAndPlayerId(request.sessionId(), playerId)
                    .orElseThrow(() -> new ApiException("Session not found or access denied"));
            save.setSession(session);
        }

        save.setPlayer(player);
        save.setSaveName(request.saveName().trim());
        save.setSaveData(request.saveData());
        save.setSaveSize(request.saveData().length());

        return toResponse(gameSaveRepository.save(save));
    }

    public GameSaveResponse toResponse(GameSave save) {
        return new GameSaveResponse(
                save.getId(),
                save.getMission() == null ? null : save.getMission().getId(),
                save.getSession() == null ? null : save.getSession().getId(),
                save.getSaveName(),
                save.getSaveData(),
                save.getSaveSize(),
                save.getCreatedAt(),
                save.getUpdatedAt()
        );
    }
}
