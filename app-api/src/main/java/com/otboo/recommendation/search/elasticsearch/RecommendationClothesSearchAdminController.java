package com.otboo.recommendation.search.elasticsearch;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 추천 검색 운영용 전체 재색인 endpoint. 일반 사용자 API가 아니다. */
@RestController
@RequestMapping("/api/admin/search/recommendation-clothes")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "otboo.recommendation.search", name = "enabled",
        havingValue = "true")
public class RecommendationClothesSearchAdminController {

    private final RecommendationClothesReindexService reindexService;

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex() throws Exception {
        RecommendationClothesReindexService.ReindexResult result = reindexService.reindexAll();
        return ResponseEntity.ok(Map.of(
                "succeeded", result.succeeded(),
                "failed", result.failed(),
                "deleted", result.deleted(),
                "failedClothesIds", result.failedClothesIds()));
    }
}
