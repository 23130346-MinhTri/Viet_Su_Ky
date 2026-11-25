package com.vietsuuky.quiz.controller;

import com.vietsuuky.account.entity.Account;
import com.vietsuuky.auth.service.AuthenticationService;
import com.vietsuuky.common.response.ApiResponse;
import com.vietsuuky.player.entity.PlayerProfile;
import com.vietsuuky.player.service.PlayerProfileService;
import com.vietsuuky.quiz.dto.QuizAnswerRequest;
import com.vietsuuky.quiz.dto.QuizAnswerResponse;
import com.vietsuuky.quiz.dto.QuizAnswerResult;
import com.vietsuuky.quiz.dto.QuizQuestionsResponse;
import com.vietsuuky.quiz.service.QuizService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/game-sessions/{sessionId}/quiz")
public class QuizController {
    private final AuthenticationService authenticationService;
    private final PlayerProfileService playerProfileService;
    private final QuizService quizService;

    public QuizController(AuthenticationService authenticationService,
                          PlayerProfileService playerProfileService,
                          QuizService quizService) {
        this.authenticationService = authenticationService;
        this.playerProfileService = playerProfileService;
        this.quizService = quizService;
    }

    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<QuizQuestionsResponse>> getQuestions(
            @AuthenticationPrincipal User user,
            @PathVariable UUID sessionId) {
        UUID playerId = currentPlayerId(user);
        return ResponseEntity.ok(ApiResponse.success(
                "Quiz questions retrieved",
                quizService.getQuestions(playerId, sessionId)));
    }

    @PostMapping("/answers")
    public ResponseEntity<ApiResponse<QuizAnswerResponse>> submitAnswer(
            @AuthenticationPrincipal User user,
            @PathVariable UUID sessionId,
            @Valid @RequestBody QuizAnswerRequest request) {
        UUID playerId = currentPlayerId(user);
        QuizAnswerResult result = quizService.submitAnswer(playerId, sessionId, request);
        HttpStatus status = result.replayed()
                ? HttpStatus.OK
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.success(
                result.replayed() ? "Quiz answer replayed" : "Quiz answer recorded",
                result.response()));
    }

    private UUID currentPlayerId(User user) {
        Account account = authenticationService.getCurrentAccount(
                UUID.fromString(user.getUsername()));
        PlayerProfile player = playerProfileService.getForAccount(account);
        return player.getId();
    }
}
