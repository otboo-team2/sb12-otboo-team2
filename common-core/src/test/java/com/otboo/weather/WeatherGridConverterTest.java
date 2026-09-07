package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WeatherGridConverterTest {

    @Test
    void convertsLatitudeAndLongitudeToKmaGrid() {
        assertThat(WeatherGridConverter.toGrid(37.5665, 126.9780))
                .isEqualTo(new WeatherGridConverter.GridCoordinate(60, 127));
    }

    @Test
    void convertsAnotherLocationToKmaGrid() {
        assertThat(WeatherGridConverter.toGrid(35.1796, 129.0756))
                .isEqualTo(new WeatherGridConverter.GridCoordinate(98, 76));
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(91, 126.9780))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidLongitude() {
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(37.5665, 181))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(Double.NaN, 126.9780))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(37.5665, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCoordinatesOutsideKmaGrid() {
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(90, 126))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsGridOutsideKmaRange() {
        assertThatThrownBy(() -> WeatherGridConverter.toCoordinate(0, 127))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsGridToCoordinateAndBackToSameGrid() {
        WeatherGridConverter.GridCoordinate grid = new WeatherGridConverter.GridCoordinate(60, 127);
        WeatherGridConverter.GeographicCoordinate center =
                WeatherGridConverter.toCoordinate(grid.x(), grid.y());

        assertThat(WeatherGridConverter.toGrid(center.latitude(), center.longitude())).isEqualTo(grid);
    }
}
