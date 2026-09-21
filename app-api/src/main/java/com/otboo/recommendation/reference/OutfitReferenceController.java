package com.otboo.recommendation.reference;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.pinterest.tag.StyleTag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations/outfit-references")
public class OutfitReferenceController {

    private final OutfitReferenceService outfitReferenceService;

    /**
     * @param styles 예: {@code styles=MINIMAL,STREET}. 하나라도 붙은 핀이 나온다. 없으면 거르지 않는다
     * @param limit  기본 12, 최대 30
     */
    @GetMapping
    public ResponseEntity<OutfitReferencesDto> find(
            @LoginUser AuthPrincipal me,
            @RequestParam UUID weatherId,
            @RequestParam(required = false) List<StyleTag> styles,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(outfitReferenceService.find(me.userId(), weatherId, styles, limit));
    }
}
