package com.otboo.weather;

public final class OpenWeatherMapWeatherMapper {

    private OpenWeatherMapWeatherMapper() {
    }

    public static SkyStatus toSkyStatus(int conditionCode, int cloudiness) {
        if (cloudiness < 0 || cloudiness > 100) {
            throw new IllegalArgumentException("운량은 0~100 범위여야 합니다.");
        }
        // 구름 외 코드는 clouds.all로 판정
        SkyStatus byConditionCode = switch (conditionCode) {
            case 800, 801, 802 -> SkyStatus.CLEAR;
            case 803 -> SkyStatus.MOSTLY_CLOUDY;
            case 804 -> SkyStatus.CLOUDY;
            default -> null;
        };
        if (byConditionCode != null) {
            return byConditionCode;
        }
        return cloudiness <= 50 ? SkyStatus.CLEAR
                : cloudiness <= 84 ? SkyStatus.MOSTLY_CLOUDY : SkyStatus.CLOUDY;
    }

    public static PrecipitationType toPrecipitationType(int conditionCode) {
        // OWM 상세 코드를 DB의 5개 강수 유형으로 변환
        if (conditionCode >= 200 && conditionCode <= 399
                || conditionCode >= 500 && conditionCode <= 511) {
            return PrecipitationType.RAIN;
        }
        if (conditionCode >= 520 && conditionCode <= 531) {
            return PrecipitationType.SHOWER;
        }
        if (conditionCode >= 611 && conditionCode <= 616) {
            return PrecipitationType.RAIN_SNOW;
        }
        if (conditionCode >= 600 && conditionCode <= 622) {
            return PrecipitationType.SNOW;
        }
        if (conditionCode >= 700 && conditionCode <= 804) {
            return PrecipitationType.NONE;
        }
        throw new IllegalArgumentException("알 수 없는 OpenWeatherMap Condition Code입니다: " + conditionCode);
    }

    public static WindStrength toWindStrength(double metersPerSecond) {
        if (!Double.isFinite(metersPerSecond) || metersPerSecond < 0) {
            throw new IllegalArgumentException("풍속은 0 이상의 유한한 값이어야 합니다.");
        }
        //  4m/s부터 보통, 9m/s부터 강함
        if (metersPerSecond < 4) {
            return WindStrength.WEAK;
        }
        if (metersPerSecond < 9) {
            return WindStrength.MODERATE;
        }
        return WindStrength.STRONG;
    }
}
