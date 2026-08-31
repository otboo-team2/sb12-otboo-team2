package com.otboo.common.pagination;

import java.util.UUID;

/**
 * 목록 조회 요청의 공통 파라미터. 모든 목록 API 가 같은 모양이다.
 *
 * <pre>
 * {@code @GetMapping("/api/clothes")}
 * public ClothesDtoCursorResponse getClothes(CursorRequest request, ...) { ... }
 * </pre>
 *
 * <h2>다음 페이지 조건을 어떻게 쓰나 (keyset)</h2>
 * 정렬 키 하나만으로는 값이 같은 행에서 순서가 흔들려 <b>같은 행이 두 번 나오거나 건너뛴다.</b>
 * 그래서 {@code (정렬키, id)} 두 개를 함께 비교한다. {@code idAfter} 가 그 tiebreaker 다.
 *
 * <pre>
 * -- sortBy=createdAt, sortDirection=DESCENDING 인 경우
 * WHERE (:cursor IS NULL)
 *    OR (f.created_at &lt; :cursor)
 *    OR (f.created_at = :cursor AND f.id &lt; :idAfter)
 * ORDER BY f.created_at DESC, f.id DESC
 * LIMIT :limit + 1
 * </pre>
 *
 * <p>오름차순이면 부등호를 뒤집는다 ({@link SortDirection#comparisonOperator()}).
 * <b>{@code ORDER BY} 에 반드시 {@code id} 를 마지막에 붙여야 한다.</b> 빠뜨리면 위 조건이 무의미해진다.
 *
 * <h2>⚠️ {@code likeCount} 정렬은 완벽히 안정적이지 않다 (의도된 선택)</h2>
 * 페이징 도중 누가 좋아요를 누르면 순위가 바뀌어 중복·누락이 생길 수 있다.
 * 복합 커서는 <b>동점 처리</b>를 해결하지만 <b>값 자체가 변하는 것</b>은 막지 못한다.
 * 완전한 안정성(스냅샷 정렬)은 구현 복잡도 대비 이득이 없어 채택하지 않았다 —
 * 트위터·인스타그램의 피드도 같은 성질을 갖는다.
 *
 * @param cursor        직전 페이지 마지막 행의 정렬 키 값. 첫 페이지면 {@code null}
 * @param idAfter       직전 페이지 마지막 행의 id (동점 tiebreaker)
 * @param limit         페이지 크기. {@link #MAX_LIMIT} 로 제한된다
 * @param sortBy        정렬 기준 필드명. 도메인마다 허용값이 다르므로 각 파트가 검증한다
 * @param sortDirection 정렬 방향
 */
public record CursorRequest(
        String cursor,
        UUID idAfter,
        int limit,
        String sortBy,
        SortDirection sortDirection
) {

    public static final int DEFAULT_LIMIT = 20;

    /**
     * 클라이언트가 {@code limit=1000000} 을 보내면 한 번의 요청으로 테이블을 통째로 긁어간다.
     * 성능 문제이면서 동시에 보안 문제라 서버에서 상한을 강제한다.
     */
    public static final int MAX_LIMIT = 100;

    public CursorRequest {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        if (sortDirection == null) {
            sortDirection = SortDirection.DESCENDING;
        }
        if (cursor != null && cursor.isBlank()) {
            cursor = null;
        }
    }

    public boolean isFirstPage() {
        return cursor == null;
    }

    /**
     * 조회할 행 수. 다음 페이지가 있는지 알기 위해 <b>한 건 더</b> 가져온다.
     * {@code count(*)} 를 한 번 더 돌리지 않으려는 것이다.
     */
    public int fetchSize() {
        return limit + 1;
    }
}
