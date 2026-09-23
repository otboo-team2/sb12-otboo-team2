package com.otboo.pinterest;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pinterest 연동 설정.
 *
 * <p>호출 정책(타임아웃·재시도)은 여기가 아니라 {@code otboo.external-api.apis.pinterest} 에 둔다.
 * 여기에는 "무엇을 부르는가"만 둔다.
 *
 * @param baseUrl     API 주소. 테스트에서 로컬 서버로 바꿔 끼우려고 열어둔다
 * @param accessToken 앱 토큰 계정의 액세스 토큰. 비어 있어도 앱은 뜬다 — 호출하는 순간에만 실패한다
 * @param boardIds    동기화할 보드 ID 목록. 이 보드들의 핀만 인덱스에 들어간다
 * @param pageSize    한 번에 받을 핀 수. Pinterest 상한은 250 이다
 */
@ConfigurationProperties(prefix = "otboo.pinterest")
public record PinterestProperties(
        String baseUrl,
        String accessToken,
        List<String> boardIds,
        Integer pageSize
) {

    public static final int MAX_PAGE_SIZE = 250;

    public PinterestProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.pinterest.com" : baseUrl;
        accessToken = accessToken == null ? "" : accessToken.strip();
        boardIds = boardIds == null ? List.of() : boardIds.stream()
                .map(String::strip)
                .filter(id -> !id.isEmpty())
                .toList();
        pageSize = pageSize == null ? 100 : Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
    }

    public boolean hasAccessToken() {
        return !accessToken.isEmpty();
    }
}
