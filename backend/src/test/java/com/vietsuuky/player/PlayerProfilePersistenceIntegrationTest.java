package com.vietsuuky.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietsuuky.account.repository.AccountRepository;
import com.vietsuuky.player.repository.PlayerProfileRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlayerProfilePersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @BeforeEach
    void cleanDatabase() {
        playerProfileRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void profileUpdateShouldPersistAndBeReturnedByReadEndpoint() throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "StrongPassword123",
                                "displayName", "New Player"
                        ))))
                .andExpect(status().isOk());

        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "StrongPassword123"
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginResponse = objectMapper.readTree(loginJson);
        String token = loginResponse.path("data").path("accessToken").asText();

        mockMvc.perform(put("/api/v1/players/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "displayName", "Updated Player",
                                "avatarUrl", "https://example.test/avatar.png"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Player"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.test/avatar.png"));

        mockMvc.perform(get("/api/v1/players/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Player"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.test/avatar.png"));
        assertThat(playerProfileRepository.count()).isEqualTo(1);
    }
}
