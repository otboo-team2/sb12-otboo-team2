package com.otboo.pinterest.tag;

import java.util.Arrays;
import java.util.Optional;

/**
 * 핀 description 의 {@code @otboo} 줄에 적히는 태그 값.
 *
 * <p>enum 이름({@code T5_8})과 description 에 적는 값({@code 5-8})을 분리한다.
 * 큐레이터가 타이핑하는 값은 사람이 읽기 쉬워야 하고, DB 에 저장되는 enum 이름은
 * 자바 식별자 규칙을 따라야 해서 둘이 같을 수 없다.
 */
public interface TagValue {

    /** description 에 적는 값. 항상 소문자다. */
    String value();

    /** 소문자로 들어온 값에 맞는 상수를 찾는다. 없으면 빈 값 — 판단은 파서가 한다. */
    static <E extends Enum<E> & TagValue> Optional<E> find(Class<E> type, String value) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.value().equals(value))
                .findFirst();
    }
}
