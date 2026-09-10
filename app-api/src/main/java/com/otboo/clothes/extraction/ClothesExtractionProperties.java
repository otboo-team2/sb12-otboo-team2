package com.otboo.clothes.extraction;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.clothes.extraction")
public record ClothesExtractionProperties(
        String geminiApiKey,
        String geminiModel,
        int maxRedirects,
        int maxHtmlBytes,
        int maxImageBytes,
        int maxTotalImageBytes,
        int maxDetailImages,
        int maxPageTextChars
) {
}
