package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CImageAnalysisConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ClothesExtractionConfig.class);

    @Test
    void b0ModeStartsWithoutCreatingDb18BeansOrNeedingTheModel() {
        context.withPropertyValues(
                "otboo.clothes.extraction.c-selector.mode=B0",
                "otboo.clothes.extraction.c-selector.model-path=")
                .run(applicationContext -> {
                    assertThat(applicationContext).hasNotFailed();
                    assertThat(applicationContext).doesNotHaveBean(Db18TextDetector.class);
                    assertThat(applicationContext).doesNotHaveBean(CImageFilter.class);
                });
    }

    @Test
    void cModeFailsToStartWhenTheConfiguredModelChecksumDoesNotMatch(
            @TempDir Path temporaryDirectory
    ) throws Exception {
        Path incorrectModel = Files.writeString(
                temporaryDirectory.resolve("wrong-model.onnx"), "not the approved model");

        context.withPropertyValues(
                "otboo.clothes.extraction.c-selector.mode=C",
                "otboo.clothes.extraction.c-selector.model-path=" + incorrectModel,
                "otboo.clothes.extraction.c-selector.model-sha256=" + "0".repeat(64))
                .run(applicationContext -> {
                    assertThat(applicationContext).hasFailed();
                    assertThat(applicationContext.getStartupFailure())
                            .hasRootCauseInstanceOf(CImageAnalysisException.class);
                });
    }
}
