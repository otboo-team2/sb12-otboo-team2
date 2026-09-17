package com.otboo.pinterest.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TempBandTest {

    @ParameterizedTest(name = "{0}°C → {1}")
    @CsvSource({
            "35, T28UP",
            "28, T28UP",
            "27.9, T23_27",
            "23, T23_27",
            "22.5, T20_22",
            "17, T17_19",
            "12, T12_16",
            "9, T9_11",
            "5, T5_8",
            "4.9, T4DOWN",
            "-15, T4DOWN"
    })
    @DisplayName("구간은 하한 기준이라 소수 기온도 빠짐없이 한 구간에 들어간다")
    void mapsCelsiusToBand(double celsius, TempBand expected) {
        assertThat(TempBand.fromCelsius(celsius)).isEqualTo(expected);
    }

    @Test
    @DisplayName("NaN · 무한대는 받지 않는다")
    void rejectsNonFinite() {
        assertThatThrownBy(() -> TempBand.fromCelsius(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TempBand.fromCelsius(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("인접 구간은 자기 자신 → 따뜻한 쪽 → 추운 쪽 순서다")
    void adjacentBandsInMiddle() {
        assertThat(TempBand.T5_8.adjacent()).containsExactly(TempBand.T5_8, TempBand.T9_11, TempBand.T4DOWN);
    }

    @Test
    @DisplayName("양 끝 구간은 이웃이 하나뿐이다")
    void adjacentBandsAtEdges() {
        assertThat(TempBand.T28UP.adjacent()).containsExactly(TempBand.T28UP, TempBand.T23_27);
        assertThat(TempBand.T4DOWN.adjacent()).containsExactly(TempBand.T4DOWN, TempBand.T5_8);
    }
}
