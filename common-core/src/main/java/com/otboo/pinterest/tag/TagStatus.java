package com.otboo.pinterest.tag;

/**
 * 핀 description 의 태그 상태.
 *
 * <p>"태그를 안 붙였다"와 "붙였는데 틀렸다"를 구분한다. 둘 다 검색에서는 빠지지만
 * 고치는 방법이 다르다 — 앞은 태그를 붙이면 되고, 뒤는 오타를 찾아야 한다.
 */
public enum TagStatus {

    /** {@code @otboo} 줄이 있고 오류가 없다. 검색 대상은 이 상태뿐이다. */
    TAGGED,

    /** {@code @otboo} 줄은 있지만 모르는 값·누락된 필수값 등 오류가 있다. */
    MALFORMED,

    /** {@code @otboo} 줄이 없다. */
    UNTAGGED
}
