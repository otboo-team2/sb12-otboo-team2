package com.otboo.common.pagination;

/**
 * 정렬 방향. Swagger 스펙의 값({@code ASCENDING} / {@code DESCENDING})을 그대로 쓴다.
 * {@code ASC} / {@code DESC} 로 줄이면 프론트가 보내는 값과 안 맞는다.
 */
public enum SortDirection {

    ASCENDING,
    DESCENDING;

    public boolean isAscending() {
        return this == ASCENDING;
    }

    /** keyset 조건에 쓸 비교 연산자. 오름차순이면 다음 페이지는 커서보다 "큰" 행들이다. */
    public String comparisonOperator() {
        return isAscending() ? ">" : "<";
    }
}
