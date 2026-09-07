package com.otboo.common.http;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URI 쿼리 스트링에 섞인 API 키를 로그에 남기기 전에 가린다.
 *
 * <p>OpenWeatherMap 의 {@code ?appid=...} 처럼 키를 쿼리 파라미터로 받는 API 가 있다.
 * 이 레포는 Public 이고 로그·스크린샷·발표자료로 키가 새는 경로가 실제로 존재한다.
 *
 * <p><b>가능하면 키는 헤더로 보내라.</b> 쿼리에 키를 넣으면 우리 로그는 가릴 수 있어도
 * 예외 메시지(예: {@code ResourceAccessException} 의 "I/O error on GET request for ...")나
 * 상대 서버의 접근 로그에는 그대로 남는다. 이 클래스는 최선의 방어일 뿐 완전하지 않다.
 */
final class UriMasker {

    /** 값을 가릴 쿼리 파라미터 이름(소문자 비교). 새 API 를 붙이며 필요하면 추가한다. */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "appid", "key", "apikey", "api_key", "access_key",
            "token", "access_token", "secret", "client_secret", "signature");

    private static final Pattern QUERY_PARAM = Pattern.compile("([^?&=]+)=([^&]*)");

    private UriMasker() {
    }

    static String mask(String uri) {
        if (uri == null || uri.indexOf('=') < 0) {
            return uri;
        }
        Matcher matcher = QUERY_PARAM.matcher(uri);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = SENSITIVE_KEYS.contains(name.toLowerCase())
                    ? name + "=***"
                    : matcher.group();
            matcher.appendReplacement(masked, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }
}
