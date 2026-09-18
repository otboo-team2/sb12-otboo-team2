package com.otboo.pinterest.tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 기온 구간. 한국에서 통용되는 "기온별 옷차림" 구간을 그대로 쓴다.
 *
 * <p>큐레이터가 이미 아는 기준이라 따로 외울 필요가 없고, 날씨 데이터와 바로 맞물린다.
 *
 * <h2>선언 순서가 곧 따뜻한 순서다</h2>
 * {@link #adjacent()} 가 {@code ordinal()} 앞뒤를 인접 구간으로 본다.
 * <b>상수 순서를 바꾸면 인접 구간 계산이 조용히 틀어진다.</b>
 *
 * <h2>경계는 하한 기준이다</h2>
 * 각 구간은 {@code [하한, 다음 구간 하한)} 이다. 27.9°C 는 {@code 23-27}, 4.5°C 는 {@code 4down} 이다.
 * 날씨 값이 소수라 "23~27" 을 정수로만 읽으면 27.5°C 가 어디에도 속하지 않게 된다.
 */
public enum TempBand implements TagValue {

    T28UP("28up", 28),
    T23_27("23-27", 23),
    T20_22("20-22", 20),
    T17_19("17-19", 17),
    T12_16("12-16", 12),
    T9_11("9-11", 9),
    T5_8("5-8", 5),
    T4DOWN("4down", Double.NEGATIVE_INFINITY);

    private final String value;
    private final double minInclusive;

    TempBand(String value, double minInclusive) {
        this.value = value;
        this.minInclusive = minInclusive;
    }

    @Override
    public String value() {
        return value;
    }

    public static TempBand fromCelsius(double celsius) {
        if (!Double.isFinite(celsius)) {
            throw new IllegalArgumentException("기온은 유한한 값이어야 한다: " + celsius);
        }
        for (TempBand band : values()) {
            if (celsius >= band.minInclusive) {
                return band;
            }
        }
        return T4DOWN;
    }
    /**
     * 자기 자신-> 한 단계 따뜻한 구간 -> 한 단계 추운 구간 순서
     * 정확히 맞는 핀이 모자랄 때 검색 범위를 넓히는 용도.
     */
    public List<TempBand> adjacent() {
        TempBand[] bands = values();
        List<TempBand> result = new ArrayList<>(3);
        result.add(this);
        if (ordinal() > 0) {
            result.add(bands[ordinal() - 1]);
        }
        if (ordinal() < bands.length - 1) {
            result.add(bands[ordinal() + 1]);
        }
        return result;
    }
}
