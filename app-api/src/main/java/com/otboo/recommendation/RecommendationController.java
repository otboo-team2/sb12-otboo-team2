package com.otboo.recommendation;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<RecommendationDto> find(
            @LoginUser AuthPrincipal me,
            @RequestParam UUID weatherId) {
        return ResponseEntity.ok(recommendationService.find(me.userId(), weatherId));
    }
}
