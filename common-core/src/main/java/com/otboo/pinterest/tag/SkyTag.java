package com.otboo.pinterest.tag;

/**
 * 하늘 상태. 옷차림이 달라지는 수준.
 * <p>{@code weathers} 테이블의 하늘 상태·강수 형태보다 거칠다.
 * "대체로 흐림"과 "흐림"에 입는 옷은 같아서, 큐레이터에게 그 차이를 고르게 하면 태그만 흔들린다.
 * 날씨 값에서 이 값으로 바꾸는 매핑은 추천 쪽 몫이다.
 */
public enum SkyTag implements TagValue {

    CLEAR("clear"),
    CLOUDY("cloudy"),
    RAIN("rain"),
    SNOW("snow");

    private final String value;

    SkyTag(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
