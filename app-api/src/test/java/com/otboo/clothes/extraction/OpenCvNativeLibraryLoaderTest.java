package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.opencv.core.Core;

class OpenCvNativeLibraryLoaderTest {

    @Test
    void loadsBundledNativeLibraryAndIsSafeToCallMoreThanOnce() throws Exception {
        assertThatCode(() -> {
            OpenCvNativeLibraryLoader.load();
            OpenCvNativeLibraryLoader.load();
        }).doesNotThrowAnyException();

        assertThat(Core.getVersionString()).isEqualTo("4.10.0");
    }
}
