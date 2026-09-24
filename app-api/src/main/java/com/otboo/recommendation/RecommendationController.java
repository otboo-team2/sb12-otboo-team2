package com.otboo.recommendation;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.recommendation.ai.AiRecommendationService;
import com.otboo.recommendation.ai.RecommendationAiRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
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
            @RequestParam(required = false) List<UUID> excludeClothesIds,
            @RequestParam(required = false) String excludedOutfits) {
        List<List<UUID>> parsedOutfits = parseExcludedOutfits(excludedOutfits);
        RecommendationDto recommendation = parsedOutfits.isEmpty()
                ? recommendationService.find(me.userId(), weatherId, excludeClothesIds)
                : recommendationService.find(me.userId(), weatherId, excludeClothesIds, parsedOutfits);
        return ResponseEntity.ok(recommendation);
    }

    @PostMapping(value = "/ai", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecommendationDto> findWithAi(
            @LoginUser AuthPrincipal me,
            @Valid @RequestBody RecommendationAiRequest request) {
        return ResponseEntity.ok(aiRecommendationService.find(me.userId(), request));
    }

    private static List<List<UUID>> parseExcludedOutfits(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(value.split(";", -1))
                    .map(outfit -> Arrays.stream(outfit.split(",", -1))
                            .map(UUID::fromString)
                            .toList())
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
