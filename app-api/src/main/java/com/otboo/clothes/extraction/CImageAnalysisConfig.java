package com.otboo.clothes.extraction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CImageAnalysisConfig {

    @Bean(destroyMethod = "close")
    public CImageAnalysisExecutor cImageAnalysisExecutor(
            CImageSelectionProperties properties
    ) {
        return new CImageAnalysisExecutor(properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "otboo.clothes.extraction.c-selector",
            name = "mode",
            havingValue = "C")
    public Db18TextDetector db18TextDetector(CImageSelectionProperties properties) {
        return new Db18TextDetector(properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "otboo.clothes.extraction.c-selector",
            name = "mode",
            havingValue = "C")
    public CImageFilter cImageFilter(Db18TextDetector detector) {
        return new CImageFilter(detector);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "otboo.clothes.extraction.c-selector",
            name = "mode",
            havingValue = "C")
    public CImageFeatureScorer cImageFeatureScorer(CImageSelectionProperties properties) {
        return new CImageFeatureScorer(properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "otboo.clothes.extraction.c-selector",
            name = "mode",
            havingValue = "C")
    public CImageSelector cImageSelector(CImageSelectionProperties properties) {
        return new CImageSelector(properties.maxSelectedImages());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "otboo.clothes.extraction.c-selector",
            name = "mode",
            havingValue = "C")
    public CImageSelectionService cImageSelectionService(
            SafeRemoteResourceClient remoteResourceClient,
            CImageFeatureScorer featureScorer,
            CImageSelector selector,
            CImageFilter filter,
            CImageAnalysisExecutor analysisExecutor,
            CImageSelectionProperties selectionProperties,
            ClothesExtractionProperties extractionProperties,
            ClothesExtractionMetrics metrics
    ) {
        return new CImageSelectionService(
                remoteResourceClient,
                featureScorer,
                selector,
                filter,
                analysisExecutor,
                selectionProperties,
                extractionProperties,
                metrics);
    }
}
