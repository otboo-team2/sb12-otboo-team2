package com.otboo.common.pagination;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * 목록 조회 응답의 공통 형태.
 * <b>필드명이 Swagger 의 {@code XxxDtoCursorResponse} 스키마와 정확히 일치해야 한다.</b>
 * 하나라도 다르면 프론트가 못 읽는다.
 *
 * <p>각 도메인은 이걸 그대로 쓰거나 {@code ClothesDtoCursorResponse} 같은 이름으로 감싸면 된다.
 *
 * <h2>사용법</h2>
 * <pre>
 * // 1. limit + 1 건을 조회한다
 * List&lt;Feed&gt; rows = repository.findSlice(request.cursor(), request.idAfter(), request.fetchSize());
 *
 * // 2. DTO 로 변환한다
 * List&lt;FeedDto&gt; dtos = rows.stream().map(FeedDto::from).toList();
 *
 * // 3. 나머지는 여기서 처리된다 (자르기 · hasNext · 다음 커서)
 * return CursorResponse.of(dtos, request, totalCount, FeedDto::createdAt, FeedDto::id);
 * </pre>
 *
 * @param <T> 데이터 타입 (엔티티가 아니라 <b>응답 DTO</b>)
 */
public record CursorResponse<T>(
        List<T> data,
        String nextCursor,
        UUID nextIdAfter,
        boolean hasNext,
        long totalCount,
        String sortBy,
        SortDirection sortDirection
) {

    /**
     * {@code limit + 1} 건이 담긴 목록을 받아 응답을 만든다.
     *
     * @param fetched          {@link CursorRequest#fetchSize()} 만큼 조회한 결과
     * @param request          원본 요청
     * @param totalCount       전체 개수
     * @param sortKeyExtractor 마지막 행에서 다음 커서로 쓸 정렬 키를 꺼내는 함수
     * @param idExtractor      마지막 행에서 id 를 꺼내는 함수
     */
    public static <T> CursorResponse<T> of(
            List<T> fetched,
            CursorRequest request,
            long totalCount,
            Function<T, Object> sortKeyExtractor,
            Function<T, UUID> idExtractor
    ) {
        boolean hasNext = fetched.size() > request.limit();
        List<T> data = hasNext
                ? List.copyOf(fetched.subList(0, request.limit()))
                : List.copyOf(fetched);

        String nextCursor = null;
        UUID nextIdAfter = null;
        if (hasNext && !data.isEmpty()) {
            T last = data.get(data.size() - 1);
            nextCursor = CursorCodec.encode(sortKeyExtractor.apply(last));
            nextIdAfter = idExtractor.apply(last);
        }

        return new CursorResponse<>(
                data, nextCursor, nextIdAfter, hasNext, totalCount,
                request.sortBy(), request.sortDirection()
        );
    }

    /** 정렬 기준이 없는 목록(예: 알림)용. */
    public static <T> CursorResponse<T> of(
            List<T> fetched,
            CursorRequest request,
            long totalCount,
            Function<T, UUID> idExtractor
    ) {
        return of(fetched, request, totalCount, idExtractor::apply, idExtractor);
    }

    public static <T> CursorResponse<T> empty(CursorRequest request) {
        return new CursorResponse<>(
                List.of(), null, null, false, 0L,
                request.sortBy(), request.sortDirection()
        );
    }
}
