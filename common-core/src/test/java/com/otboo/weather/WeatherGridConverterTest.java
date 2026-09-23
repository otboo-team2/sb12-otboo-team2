package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.common.exception.BusinessException;
import com.otboo.weather.exception.WeatherErrorCode;
import java.util.stream.Stream;
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
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.INVALID_COORDINATE);
    }

    @Test
    void rejectsInvalidLongitude() {
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(37.5665, 181))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.INVALID_COORDINATE);
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(Double.NaN, 126.9780))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.INVALID_COORDINATE);
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(37.5665, Double.POSITIVE_INFINITY))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.INVALID_COORDINATE);
    }

    @Test
    void rejectsCoordinatesOutsideKmaGrid() {
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(90, 126))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.UNSUPPORTED_LOCATION);
    }

    @Test
    void rejectsGridOutsideKmaRange() {
        assertThatThrownBy(() -> WeatherGridConverter.toCoordinate(0, 127))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.UNSUPPORTED_LOCATION);
    }

    @Test
    void rejectsLocationOutsideSupportedGrid() {
        assertThatThrownBy(() -> WeatherGridConverter.toGrid(40.7128, -74.0060))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.UNSUPPORTED_LOCATION);
    }

    @Test
    void filtersUnsupportedAndInvalidLocationsAndContinuesCollection() {
        var grids = Stream.of(
                        new double[]{37.5665, 126.9780},
                        new double[]{40.7128, -74.0060},
                        new double[]{Double.NaN, 126},
                        new double[]{91, 126},
                        new double[]{-90, 126},
                        new double[]{35.1796, 129.0756})
                .flatMap(coordinate -> WeatherGridConverter
                        .tryToGrid(coordinate[0], coordinate[1]).stream())
                .toList();

        assertThat(grids).containsExactly(
                new WeatherGridConverter.GridCoordinate(60, 127),
                new WeatherGridConverter.GridCoordinate(98, 76));
    }

    @Test
    void convertsGridToCoordinateAndBackToSameGrid() {
        WeatherGridConverter.GridCoordinate grid = new WeatherGridConverter.GridCoordinate(60, 127);
        WeatherGridConverter.GeographicCoordinate center =
                WeatherGridConverter.toCoordinate(grid.x(), grid.y());

        assertThat(WeatherGridConverter.toGrid(center.latitude(), center.longitude())).isEqualTo(grid);
    }
}
