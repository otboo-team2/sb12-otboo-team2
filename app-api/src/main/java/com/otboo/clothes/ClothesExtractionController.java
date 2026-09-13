package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesExtractionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구매 링크를 분석한 후보 의상 정보를 반환하는 API다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes/extractions")
public class ClothesExtractionController {

    private final ClothesExtractionService clothesExtractionService;

    @GetMapping
    public ClothesExtractionDto extract(@RequestParam String url) {
        return clothesExtractionService.extract(url);
    }
}
