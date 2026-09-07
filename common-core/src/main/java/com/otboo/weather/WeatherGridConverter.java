package com.otboo.weather;

public final class WeatherGridConverter {

    // 기상청 단기예보 격자 변환에 사용하는 Lambert Conformal Conic 상수
    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_SPACING_KM = 5.0;
    private static final double FIRST_STANDARD_PARALLEL = Math.toRadians(30.0);
    private static final double SECOND_STANDARD_PARALLEL = Math.toRadians(60.0);
    private static final double ORIGIN_LONGITUDE = Math.toRadians(126.0);
    private static final double ORIGIN_LATITUDE = Math.toRadians(38.0);
    private static final double ORIGIN_X = 43.0;
    private static final double ORIGIN_Y = 136.0;
    private static final int MIN_GRID_X = 1;
    private static final int MAX_GRID_X = 149;
    private static final int MIN_GRID_Y = 1;
    private static final int MAX_GRID_Y = 253;

    private static final double GRID_RADIUS = EARTH_RADIUS_KM / GRID_SPACING_KM;
    private static final double CONE = Math.log(
            Math.cos(FIRST_STANDARD_PARALLEL) / Math.cos(SECOND_STANDARD_PARALLEL))
            / Math.log(Math.tan(Math.PI / 4 + SECOND_STANDARD_PARALLEL / 2)
            / Math.tan(Math.PI / 4 + FIRST_STANDARD_PARALLEL / 2));
    private static final double SCALE = Math.pow(
            Math.tan(Math.PI / 4 + FIRST_STANDARD_PARALLEL / 2), CONE)
            * Math.cos(FIRST_STANDARD_PARALLEL) / CONE;
    private static final double ORIGIN_RADIUS = GRID_RADIUS * SCALE
            / Math.pow(Math.tan(Math.PI / 4 + ORIGIN_LATITUDE / 2), CONE);

    private WeatherGridConverter() {
    }

    public static GridCoordinate toGrid(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("위도는 -90~90, 경도는 -180~180 범위여야 합니다.");
        }

        double radius = GRID_RADIUS * SCALE
                / Math.pow(Math.tan(Math.PI / 4 + Math.toRadians(latitude) / 2), CONE);
        double theta = Math.toRadians(longitude) - ORIGIN_LONGITUDE;
        if (theta > Math.PI) {
            theta -= 2 * Math.PI;
        } else if (theta < -Math.PI) {
            theta += 2 * Math.PI;
        }
        theta *= CONE;

        // 가장 가까운 격자점으로 반올림
        int x = (int) Math.floor(radius * Math.sin(theta) + ORIGIN_X + 0.5);
        int y = (int) Math.floor(ORIGIN_RADIUS - radius * Math.cos(theta) + ORIGIN_Y + 0.5);
        validateGrid(x, y);
        return new GridCoordinate(x, y);
    }

    public static GeographicCoordinate toCoordinate(int x, int y) {
        validateGrid(x, y);
        // 정수 격자점을 위경도로 역변환
        double horizontal = x - ORIGIN_X;
        double vertical = ORIGIN_RADIUS - y + ORIGIN_Y;
        double radius = Math.hypot(horizontal, vertical);
        double latitude = 2 * Math.atan(Math.pow(GRID_RADIUS * SCALE / radius, 1 / CONE))
                - Math.PI / 2;
        double theta = Math.atan2(horizontal, vertical);
        double longitude = theta / CONE + ORIGIN_LONGITUDE;

        return new GeographicCoordinate(Math.toDegrees(latitude), Math.toDegrees(longitude));
    }

    private static void validateGrid(int x, int y) {
        if (x < MIN_GRID_X || x > MAX_GRID_X || y < MIN_GRID_Y || y > MAX_GRID_Y) {
            throw new IllegalArgumentException("기상청 격자는 x 1~149, y 1~253 범위여야 합니다.");
        }
    }

    public record GridCoordinate(int x, int y) {
    }

    public record GeographicCoordinate(double latitude, double longitude) {
    }
}
