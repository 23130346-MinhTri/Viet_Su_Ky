package com.vietsuuky.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietsuuky.account.entity.Account;
import com.vietsuuky.account.entity.AccountStatus;
import com.vietsuuky.account.repository.AccountRepository;
import com.vietsuuky.player.repository.PlayerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class AuthPlayerHttpIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

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
    void shouldRunRegisterLoginAndProfileFlowOverHttp() throws Exception {
        String email = "http-" + UUID.randomUUID() + "@example.test";
        String password = "StrongPassword123";

        ResponseEntity<JsonNode> registration = restTemplate.postForEntity(
                "/api/v1/auth/register",
                Map.of("email", email, "password", password, "displayName", "HTTP Player"),
                JsonNode.class
        );
        assertThat(registration.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(registration.getBody().path("data").path("email").asText()).isEqualTo(email);

        ResponseEntity<JsonNode> login = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", email, "password", password),
                JsonNode.class
        );
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        String token = login.getBody().path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> authenticatedRequest = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> initialProfile = restTemplate.exchange(
                "/api/v1/players/me",
                HttpMethod.GET,
                authenticatedRequest,
                JsonNode.class
        );
        assertThat(initialProfile.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(initialProfile.getBody().path("data").path("displayName").asText()).isEqualTo("HTTP Player");

        HttpEntity<String> updateRequest = new HttpEntity<>(
                objectMapper.writeValueAsString(Map.of(
                        "displayName", "HTTP Player Updated",
                        "avatarUrl", "https://example.test/http-avatar.png"
                )),
                headers
        );
        ResponseEntity<JsonNode> updatedProfile = restTemplate.exchange(
                "/api/v1/players/me",
                HttpMethod.PUT,
                updateRequest,
                JsonNode.class
        );
        assertThat(updatedProfile.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> persistedProfile = restTemplate.exchange(
                "/api/v1/players/me",
                HttpMethod.GET,
                authenticatedRequest,
                JsonNode.class
        );
        assertThat(persistedProfile.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(persistedProfile.getBody().path("data").path("displayName").asText()).isEqualTo("HTTP Player Updated");
        assertThat(persistedProfile.getBody().path("data").path("avatarUrl").asText())
                .isEqualTo("https://example.test/http-avatar.png");

        ResponseEntity<JsonNode> anonymousProfile = restTemplate.getForEntity("/api/v1/players/me", JsonNode.class);
        assertThat(anonymousProfile.getStatusCode().is4xxClientError()).isTrue();
        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(playerProfileRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectLockedOrDisabledAccountsEvenWithPreviouslyIssuedTokens() {
        String email = "status-" + UUID.randomUUID() + "@example.test";
        String token = registerAndLogin(email, "Status Player");
        Account account = accountRepository.findByEmail(email).orElseThrow();

        assertProfileRequestStatus(token, 200);

        account.setStatus(AccountStatus.LOCKED);
        accountRepository.saveAndFlush(account);
        assertProfileRequestRejected(token);
        assertLoginRejected(email);

        account.setStatus(AccountStatus.DISABLED);
        accountRepository.saveAndFlush(account);
        assertProfileRequestRejected(token);
        assertLoginRejected(email);
    }

    @Test
    void profileUpdatesShouldOnlyAffectTheAuthenticatedPlayer() {
        String firstEmail = "owner-" + UUID.randomUUID() + "@example.test";
        String secondEmail = "other-" + UUID.randomUUID() + "@example.test";
        String firstToken = registerAndLogin(firstEmail, "First Player");
        String secondToken = registerAndLogin(secondEmail, "Second Player");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secondToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> update = restTemplate.exchange(
                "/api/v1/players/me",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("displayName", "Changed Second Player"), headers),
                JsonNode.class
        );
        assertThat(update.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(getProfile(firstToken).path("data").path("displayName").asText()).isEqualTo("First Player");
        assertThat(getProfile(secondToken).path("data").path("displayName").asText())
                .isEqualTo("Changed Second Player");
    }

    private String registerAndLogin(String email, String displayName) {
        String password = "StrongPassword123";
        ResponseEntity<JsonNode> registration = restTemplate.postForEntity(
                "/api/v1/auth/register",
                Map.of("email", email, "password", password, "displayName", displayName),
                JsonNode.class
        );
        assertThat(registration.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> login = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", email, "password", password),
                JsonNode.class
        );
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        return login.getBody().path("data").path("accessToken").asText();
    }

    private void assertProfileRequestStatus(String token, int expectedStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/v1/players/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    private void assertProfileRequestRejected(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/v1/players/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        );
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    private void assertLoginRejected(String email) {
        ResponseEntity<JsonNode> login = restTemplate.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "StrongPassword123"),
                JsonNode.class
        );
        assertThat(login.getStatusCode().is4xxClientError()).isTrue();
    }

    private JsonNode getProfile(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(
                "/api/v1/players/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
        ).getBody();
    }
}
