package com.otboo.recommendation;

import com.otboo.recommendation.ai.AiRecommendationService;
import com.otboo.recommendation.ai.RecommendationAiRequest;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final AiRecommendationService aiRecommendationService;

    @GetMapping
    public ResponseEntity<RecommendationDto> find(
            @LoginUser AuthPrincipal me,
            @RequestParam UUID weatherId,
            @RequestParam(required = false) List<UUID> excludeClothesIds) {
        return ResponseEntity.ok(recommendationService.find(
                me.userId(), weatherId, excludeClothesIds));
    }

    @PostMapping(value = "/ai", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecommendationDto> findWithAi(
            @LoginUser AuthPrincipal me,
            @Valid @RequestBody RecommendationAiRequest request) {
        return ResponseEntity.ok(aiRecommendationService.find(me.userId(), request));
    }
}
