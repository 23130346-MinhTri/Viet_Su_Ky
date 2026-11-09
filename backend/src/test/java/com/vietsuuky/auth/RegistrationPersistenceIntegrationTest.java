package com.vietsuuky.auth;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationPersistenceIntegrationTest {

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
    void registrationAndLoginShouldPersistAccountAndProfile() throws Exception {
        String email = "register-" + UUID.randomUUID() + "@example.test";
        String password = "StrongPassword123";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password,
                                "displayName", "Phase Player"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.displayName").value("Phase Player"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        assertThat(accountRepository.findByEmail(email)).isPresent();
        assertThat(playerProfileRepository.count()).isEqualTo(1);

        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginResponse = objectMapper.readTree(loginJson);
        String token = loginResponse.path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/v1/players/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Phase Player"))
                .andExpect(jsonPath("$.data.email").value(email));
    }
}
