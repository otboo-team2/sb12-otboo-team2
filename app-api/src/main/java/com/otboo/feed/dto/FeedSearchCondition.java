package com.otboo.feed.dto;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.SkyStatus;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 피드 목록 조회 조건. 페이지네이션 파라미터({@link CursorRequest})와 필터를 함께 들고 다닌다.
 *
 * <p>검색 구현체가 이 하나만 보고 질의를 만들 수 있어야 엔진을 바꿔 끼울 때 서비스 코드가 그대로 남는다.
 *
 * <h2>필터는 enum 으로 들고 있고, 문자열 → enum 변환은 {@link #of} 가 한다</h2>
 * 쿼리 파라미터는 문자열로 들어온다. 그 값을 그대로 SQL 에 넘기면 프론트 오타가
 * <b>조용히 "결과 0건"</b> 으로 나타나 디버깅이 오래 걸린다. 그래서 경계에서 한 번 변환하며 검증하고,
 * 그 뒤로는 {@link SkyStatus}·{@link PrecipitationType} 타입으로만 다룬다.
 *
 * <p>컨트롤러 파라미터를 바로 enum 으로 바인딩하지 않은 이유는 <b>에러 메시지</b> 때문이다.
 * 스프링 기본 변환 실패는 타입 불일치(COMMON_003)로 끝나 어떤 값이 허용되는지 알려주지 않는다.
 * 여기서 잡으면 {@code allowed} 에 후보를 담아 돌려줄 수 있다.
 */
public record FeedSearchCondition(
        CursorRequest page,
        String keywordLike,
        SkyStatus skyStatusEqual,
        PrecipitationType precipitationTypeEqual,
        UUID authorIdEqual
) {

    public static final String SORT_BY_CREATED_AT = "createdAt";
    public static final String SORT_BY_LIKE_COUNT = "likeCount";

    private static final Set<String> SORTABLE = Set.of(SORT_BY_CREATED_AT, SORT_BY_LIKE_COUNT);

    public FeedSearchCondition {
        keywordLike = blankToNull(keywordLike);

        String sortBy = page.sortBy() == null ? SORT_BY_CREATED_AT : page.sortBy();
        if (!SORTABLE.contains(sortBy)) {
            throw invalid("sortBy", sortBy, SORTABLE);
        }
        page = new CursorRequest(
                page.cursor(), page.idAfter(), page.limit(), sortBy, page.sortDirection());
    }

    /** 쿼리 파라미터(문자열)로부터 조건을 만든다. 허용하지 않는 값이면 400 으로 거절한다. */
    public static FeedSearchCondition of(
            CursorRequest page,
            String keywordLike,
            String skyStatusEqual,
            String precipitationTypeEqual,
            UUID authorIdEqual
    ) {
        return new FeedSearchCondition(
                page,
                keywordLike,
                parse(SkyStatus.class, skyStatusEqual, "skyStatusEqual"),
                parse(PrecipitationType.class, precipitationTypeEqual, "precipitationTypeEqual"),
                authorIdEqual);
    }

    public boolean sortsByLikeCount() {
        return SORT_BY_LIKE_COUNT.equals(page.sortBy());
    }

    public boolean hasKeyword() {
        return keywordLike != null;
    }

    /** 날씨 조건이 하나라도 있으면 조회 시 weathers 를 조인해야 한다. */
    public boolean hasWeatherFilter() {
        return skyStatusEqual != null || precipitationTypeEqual != null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, trimmed);
        } catch (IllegalArgumentException e) {
            throw invalid(field, trimmed, Arrays.stream(type.getEnumConstants())
                    .map(Enum::name).collect(Collectors.toSet()));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BusinessException invalid(String field, String value, Set<String> allowed) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE)
                .addDetail(field, value)
                .addDetail("allowed", String.join(", ", allowed));
    }
}
