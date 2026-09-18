package com.otboo.pinterest.client;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.pinterest.PinterestProperties;
import com.otboo.pinterest.exception.PinterestErrorCode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Pinterest API v5 클라이언트.
 *
 * <h2>Pinterest 전체를 검색하는 API 가 아니다</h2>
 * 키워드로 Pinterest 전체를 검색하는 {@code GET /v5/search/partner/pins} 는 베타라서
 * 일반 앱에는 열리지 않는다. 그래서 이 클라이언트는 <b>토큰 계정이 볼 수 있는 핀</b>만 읽는다.
 * <ul>
 *   <li>{@link #listBoardPins} — 보드의 핀 목록. 토큰 계정이 멤버인 그룹 보드도 포함된다. <b>동기화의 주 경로</b></li>
 *   <li>{@link #searchPins} — 토큰 계정의 핀 검색. 그룹 보드에서 다른 사람이 올린 핀도 잡히는지는
 *       스펙에 적혀 있지 않다. 폴백 용도로만 쓴다</li>
 * </ul>
 * 코디 추천 검색은 Pinterest 가 아니라 동기화한 우리 DB({@code pinterest_pins})에서 한다.
 * Trial 등급은 앱 단위 하루 호출 상한이 있어서, 사용자 요청마다 Pinterest 를 부를 수 없다.
 *
 * <h2>토큰이 없어도 앱은 뜬다</h2>
 * 토큰이 없는 로컬에서 다른 기능 작업이 막히면 안 된다. 호출하는 순간에만 실패한다.
 *
 * <h2>URI 는 템플릿 변수로 넘긴다</h2>
 * 검색어와 bookmark 를 문자열에 직접 붙이지 않는다. {@code &} · {@code +} 같은 문자가 쿼리를 깨고,
 * 미리 인코딩하면 RestClient 가 한 번 더 인코딩한다. 로그에도 템플릿만 남아 검색어가 찍히지 않는다.
 */
@Component
public class PinterestClient {

    static final String API_NAME = "pinterest";

    /** Pinterest 의 보드·핀 ID 는 숫자 문자열이다. 경로에 들어가므로 다른 문자는 받지 않는다. */
    private static final Pattern NUMERIC_ID = Pattern.compile("^\\d{1,64}$");

    private static final int MAX_QUERY_LENGTH = 200;

    private static final ParameterizedTypeReference<PinterestPageResponse<PinterestPinResponse>> PIN_PAGE =
            new ParameterizedTypeReference<>() {
            };

    private final ExternalApiClient api;
    private final PinterestProperties properties;

    @Autowired
    public PinterestClient(ExternalApiClientFactory factory, PinterestProperties properties) {
        this(factory.create(API_NAME, properties.baseUrl()), properties);
    }

    PinterestClient(ExternalApiClient api, PinterestProperties properties) {
        this.api = api;
        this.properties = properties;
    }

    /** 보드의 핀 한 페이지. {@code bookmark} 가 {@code null} 이면 첫 페이지다. */
    public PinterestPageResponse<PinterestPinResponse> listBoardPins(String boardId, String bookmark) {
        if (boardId == null || !NUMERIC_ID.matcher(boardId).matches()) {
            throw new BusinessException(PinterestErrorCode.INVALID_BOARD_ID)
                    .addDetail("boardId", String.valueOf(boardId));
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("boardId", boardId);
        variables.put("pageSize", properties.pageSize());
        return getPage("/v5/boards/{boardId}/pins?page_size={pageSize}", variables, bookmark);
    }

    /** 토큰 계정의 핀을 description 키워드로 검색한다. 폴백 용도다 — 클래스 설명 참고. */
    public PinterestPageResponse<PinterestPinResponse> searchPins(String query, String bookmark) {
        if (query == null || query.isBlank() || query.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException(PinterestErrorCode.INVALID_SEARCH_QUERY);
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query.strip());
        return getPage("/v5/search/pins?query={query}", variables, bookmark);
    }

    private PinterestPageResponse<PinterestPinResponse> getPage(
            String template, Map<String, Object> variables, String bookmark) {
        if (!properties.hasAccessToken()) {
            throw new BusinessException(PinterestErrorCode.ACCESS_TOKEN_NOT_CONFIGURED);
        }
        String uri = template;
        if (bookmark != null && !bookmark.isBlank()) {
            uri += "&bookmark={bookmark}";
            variables.put("bookmark", bookmark);
        }
        String finalUri = uri;
        PinterestPageResponse<PinterestPinResponse> page = api.exchange("GET " + finalUri,
                client -> client.get()
                        .uri(finalUri, variables)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                        .retrieve()
                        .body(PIN_PAGE));
        if (page == null) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR)
                    .addDetail("api", API_NAME)
                    .addDetail("reason", "빈 응답");
        }
        return page;
    }
}
