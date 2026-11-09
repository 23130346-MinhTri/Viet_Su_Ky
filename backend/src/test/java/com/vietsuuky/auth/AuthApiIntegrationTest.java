package com.vietsuuky.auth;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @BeforeEach
    void setUp() {
        playerProfileRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void register_shouldCreateAccountAndPlayerProfile() throws Exception {
        Map<String, Object> payload = Map.of(
                "email", "player@example.com",
                "password", "StrongPassword123",
                "displayName", "PlayerOne"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data.email").value("player@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("PlayerOne"));

        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(playerProfileRepository.count()).isEqualTo(1);
    }

    @Test
    void login_shouldIssueJwt_forValidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "login@example.com",
                                "password", "StrongPassword123",
                                "displayName", "LoginUser"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "login@example.com",
                                "password", "StrongPassword123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.account.email").value("login@example.com"));
    }
}
