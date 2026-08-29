package com.otboo.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CursorResponseTest {

    private record Row(UUID id, Instant createdAt, long likeCount) {

        static Row of(int seq) {
            return new Row(UUID.randomUUID(), Instant.parse("2026-09-02T00:00:00Z").plusSeconds(seq), seq);
        }
    }

    private static List<Row> rows(int count) {
        return IntStream.range(0, count).mapToObj(Row::of).toList();
    }

    private static CursorRequest request(int limit) {
        return new CursorRequest(null, null, limit, "createdAt", SortDirection.DESCENDING);
    }

    @Nested
    @DisplayName("다음 페이지 판정")
    class HasNext {

        @Test
        @DisplayName("limit 보다 한 건 더 왔으면 다음 페이지가 있고, 초과분은 잘라낸다")
        void trimsExtraRow() {
            CursorRequest req = request(3);
            CursorResponse<Row> response = CursorResponse.of(
                    rows(4), req, 100L, Row::createdAt, Row::id);

            assertThat(response.hasNext()).isTrue();
            assertThat(response.data()).hasSize(3);
        }

        @Test
        @DisplayName("limit 이하면 마지막 페이지다")
        void lastPage() {
            CursorRequest req = request(3);
            CursorResponse<Row> response = CursorResponse.of(
                    rows(2), req, 2L, Row::createdAt, Row::id);

            assertThat(response.hasNext()).isFalse();
            assertThat(response.data()).hasSize(2);
        }

        @Test
        @DisplayName("결과가 없으면 커서를 만들지 않는다")
        void emptyResult() {
            CursorResponse<Row> response = CursorResponse.of(
                    List.of(), request(3), 0L, Row::createdAt, Row::id);

            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
            assertThat(response.nextIdAfter()).isNull();
        }
    }

    @Nested
    @DisplayName("다음 커서")
    class NextCursor {

        @Test
        @DisplayName("잘라낸 뒤 마지막 행에서 뽑는다 — 초과분에서 뽑으면 한 건이 통째로 누락된다")
        void takenFromLastVisibleRow() {
            List<Row> fetched = rows(4);
            CursorResponse<Row> response = CursorResponse.of(
                    fetched, request(3), 100L, Row::createdAt, Row::id);

            Row lastVisible = fetched.get(2);
            assertThat(response.nextCursor()).isEqualTo(lastVisible.createdAt().toString());
            assertThat(response.nextIdAfter()).isEqualTo(lastVisible.id());
        }

        @Test
        @DisplayName("마지막 페이지면 다음 커서가 없다")
        void nullOnLastPage() {
            CursorResponse<Row> response = CursorResponse.of(
                    rows(2), request(3), 2L, Row::createdAt, Row::id);

            assertThat(response.nextCursor()).isNull();
            assertThat(response.nextIdAfter()).isNull();
        }

        @Test
        @DisplayName("숫자 정렬 키(likeCount)도 커서로 쓸 수 있다")
        void numericSortKey() {
            List<Row> fetched = rows(3);
            CursorRequest req = new CursorRequest(null, null, 2, "likeCount", SortDirection.DESCENDING);

            CursorResponse<Row> response = CursorResponse.of(
                    fetched, req, 50L, Row::likeCount, Row::id);

            assertThat(response.nextCursor()).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("요청 파라미터 보정")
    class RequestNormalization {

        @Test
        @DisplayName("limit 상한을 넘기면 잘라낸다 — 한 요청으로 테이블 전체를 긁어가지 못하게")
        void clampsLimit() {
            CursorRequest req = new CursorRequest(null, null, 1_000_000, "createdAt", SortDirection.DESCENDING);
            assertThat(req.limit()).isEqualTo(CursorRequest.MAX_LIMIT);
        }

        @Test
        @DisplayName("limit 이 0 이하면 기본값을 쓴다")
        void defaultsLimit() {
            assertThat(new CursorRequest(null, null, 0, "createdAt", null).limit())
                    .isEqualTo(CursorRequest.DEFAULT_LIMIT);
        }

        @Test
        @DisplayName("정렬 방향이 없으면 최신순이 기본이다")
        void defaultsDirection() {
            assertThat(new CursorRequest(null, null, 10, "createdAt", null).sortDirection())
                    .isEqualTo(SortDirection.DESCENDING);
        }

        @Test
        @DisplayName("빈 문자열 커서는 첫 페이지로 본다")
        void blankCursorIsFirstPage() {
            assertThat(new CursorRequest("  ", null, 10, "createdAt", null).isFirstPage()).isTrue();
        }

        @Test
        @DisplayName("한 건 더 조회해서 다음 페이지 유무를 판정한다")
        void fetchSizeIsLimitPlusOne() {
            assertThat(request(20).fetchSize()).isEqualTo(21);
        }
    }

    @Nested
    @DisplayName("커서 값 해석")
    class Codec {

        @Test
        @DisplayName("시각은 UTC ISO-8601 로 왕복한다")
        void instantRoundTrip() {
            Instant value = Instant.parse("2026-09-02T04:30:00Z");
            assertThat(CursorCodec.asInstant(CursorCodec.encode(value))).isEqualTo(value);
        }

        @Test
        @DisplayName("숫자도 왕복한다")
        void longRoundTrip() {
            assertThat(CursorCodec.asLong(CursorCodec.encode(42L))).isEqualTo(42L);
        }

        @Test
        @DisplayName("깨진 커서는 COMMON_004 로 거절한다 — 500 이 아니라 400 이어야 한다")
        void rejectsMalformedCursor() {
            assertThatThrownBy(() -> CursorCodec.asInstant("어제쯤"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", com.otboo.common.exception.CommonErrorCode.INVALID_CURSOR);
        }

        @Test
        @DisplayName("빈 커서는 null 로 본다")
        void blankIsNull() {
            assertThat(CursorCodec.asInstant(null)).isNull();
            assertThat(CursorCodec.asLong(" ")).isNull();
        }
    }

    @Nested
    @DisplayName("정렬 방향")
    class Direction {

        @Test
        @DisplayName("오름차순이면 다음 페이지는 커서보다 큰 행이다")
        void operator() {
            assertThat(SortDirection.ASCENDING.comparisonOperator()).isEqualTo(">");
            assertThat(SortDirection.DESCENDING.comparisonOperator()).isEqualTo("<");
        }
    }
}
