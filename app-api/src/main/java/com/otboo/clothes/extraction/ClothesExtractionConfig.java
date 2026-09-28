package com.otboo.clothes.extraction;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties({
        ClothesExtractionProperties.class,
        CImageSelectionProperties.class
})
@Import(CImageAnalysisConfig.class)
public class ClothesExtractionConfig {
}
