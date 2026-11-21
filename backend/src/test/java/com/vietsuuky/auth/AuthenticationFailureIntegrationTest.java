package com.vietsuuky.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietsuuky.account.repository.AccountRepository;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import com.vietsuuky.progress.repository.MissionProgressRepository;
import com.vietsuuky.progress.repository.PlayerProgressRepository;
import com.vietsuuky.reward.repository.PlayerRewardRepository;
import com.vietsuuky.save.repository.GameSaveRepository;
import com.vietsuuky.session.repository.GameSessionRepository;
import com.vietsuuky.session.repository.IdempotencyRecordRepository;
import com.vietsuuky.testsupport.TestDatabaseCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private GameSessionRepository gameSessionRepository;

    @Autowired
    private GameSaveRepository gameSaveRepository;

    @Autowired
    private MissionProgressRepository missionProgressRepository;

    @Autowired
    private PlayerProgressRepository playerProgressRepository;

    @Autowired
    private PlayerRewardRepository playerRewardRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void cleanDatabase() {
        TestDatabaseCleanup.clearPlayerGraph(
                accountRepository,
                playerProfileRepository,
                gameSessionRepository,
                gameSaveRepository,
                missionProgressRepository,
                playerProgressRepository,
                playerRewardRepository,
                idempotencyRecordRepository
        );
    }

    @Test
    void duplicateRegistrationAndInvalidPasswordShouldBeRejected() throws Exception {
        String email = "failure-" + UUID.randomUUID() + "@example.test";
        String registration = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "StrongPassword123",
                "displayName", "Failure Test"
        ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "WrongPassword123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "StrongPassword123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }
}
