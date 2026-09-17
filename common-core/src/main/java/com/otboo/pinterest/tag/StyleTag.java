package com.otboo.pinterest.tag;

/**
 * 코디 스타일.
 *
 * <h2>7개에서 늘리지 않는다</h2>
 * 큐레이터 여러 명이 "이건 미니멀인가 클래식인가"로 흔들리기 시작하면 태그가 쓸모없어진다.
 * 항목이 늘수록 그 흔들림이 커진다. 애매하면 값을 여러 개 붙인다({@code style:minimal,classic}).
 *
 * <p>LLM 은 이 목록 밖의 값을 자주 지어낸다({@code minimalist_street} 등).
 * 추천 쪽에서 LLM 응답을 반드시 이 목록으로 걸러야 한다.
 */
public enum StyleTag implements TagValue {

    MINIMAL("minimal"),
    STREET("street"),
    CASUAL("casual"),
    CLASSIC("classic"),
    FORMAL("formal"),
    SPORTY("sporty")
;

    private final String value;

    StyleTag(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
