package com.otboo.feed.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 검색어를 엔진이 이해하는 형태로 바꾼다. MySQL 과 OpenSearch 가 같은 규칙을 쓰도록 한 곳에 모은다.
 *
 * <h2>왜 사용자 입력을 그대로 넘기면 안 되나</h2>
 * MySQL 의 {@code IN BOOLEAN MODE} 는 {@code + - > < ( ) ~ * " @} 를 연산자로 해석한다.
 * 사용자가 "C++" 이나 "@집앞" 을 치면 문법 오류로 <b>쿼리 자체가 실패</b>한다.
 * 검색창에 뭘 넣든 500 이 나면 안 되므로 연산자 문자를 걷어낸다.
 *
 * <h2>구문 검색("...")을 쓰지 않는 이유 — 실측 결과</h2>
 * 로컬 MySQL 8.0 + ngram 인덱스에 실제 데이터를 넣고 확인한 결과,
 * {@code AGAINST('"코트"' IN BOOLEAN MODE)} 가 "겨울코트를 꺼냈다" 를 <b>찾지 못했다.</b>
 * 반면 따옴표 없는 {@code AGAINST('코트')} 는 정상적으로 찾았다.
 * ngram 파서에서 구문(proximity) 검색의 동작이 신뢰할 만하지 않다는 뜻이라 의존하지 않는다.
 *
 * <h2>대신 쓰는 방법: ngram 선별 + LIKE 정밀 확인</h2>
 * <ol>
 *   <li>검색어를 2글자 토큰으로 쪼개 <b>전부 포함(AND)</b> 조건을 만든다.
 *       "겨울코트" → {@code +겨울 +울코 +코트}. 부분 문자열을 포함하는 문서라면
 *       그 2그램을 반드시 전부 갖고 있으므로 <b>놓치는 결과가 없다.</b></li>
 *   <li>토큰이 흩어져 걸린 문서(예: "겨울에 코트말고" )를 걸러내려고
 *       {@code LIKE '%검색어%'} 를 한 번 더 건다. 이미 1단계가 후보를 인덱스로 좁혔기 때문에
 *       <b>전체 스캔이 되지 않는다.</b></li>
 * </ol>
 * 결과적으로 인덱스의 속도와 {@code LIKE} 의 정확도를 모두 얻는다.
 */
public final class SearchKeyword {

    /** MySQL FULLTEXT 의 ngram 파서 기본 토큰 크기({@code ngram_token_size}). 로컬 확인값도 2다. */
    public static final int NGRAM_TOKEN_SIZE = 2;

    private static final String BOOLEAN_OPERATORS = "[+\\-><()~*\"@\\\\]";

    private SearchKeyword() {
    }

    /** 불리언 모드 연산자를 공백으로 바꾸고 공백을 정리한다. */
    public static String sanitize(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.replaceAll(BOOLEAN_OPERATORS, " ").trim().replaceAll("\\s+", " ");
    }

    /**
     * FULLTEXT 인덱스로 후보를 좁힐 수 있는 검색어인지.
     *
     * <p>낱말 하나라도 {@link #NGRAM_TOKEN_SIZE} 보다 짧으면 그 낱말은 색인에 토큰이 없어
     * AND 조건이 항상 0건이 된다. 그 경우만 {@code LIKE} 단독으로 내려간다 —
     * 느리지만 결과가 나오는 쪽이 맞다.
     */
    public static boolean isFullTextSearchable(String keyword) {
        List<String> words = words(keyword);
        return !words.isEmpty() && words.stream().allMatch(w -> w.length() >= NGRAM_TOKEN_SIZE);
    }

    /**
     * 불리언 모드 AND 식으로 바꾼다. {@code "겨울코트"} → {@code "+겨울 +울코 +코트"}.
     *
     * <p>각 토큰이 정확히 {@link #NGRAM_TOKEN_SIZE} 글자라 ngram 색인의 토큰과 1:1 로 맞는다.
     * {@code +} 는 "반드시 포함" 이라 토큰이 OR 로 흩어지지 않는다.
     */
    public static String toBooleanAnd(String keyword) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String word : words(keyword)) {
            for (int i = 0; i + NGRAM_TOKEN_SIZE <= word.length(); i++) {
                tokens.add(word.substring(i, i + NGRAM_TOKEN_SIZE));
            }
        }
        List<String> terms = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            terms.add("+" + token);
        }
        return String.join(" ", terms);
    }

    /**
     * {@code LIKE} 패턴.
     *
     * <p><b>MATCH 와 똑같이 {@link #sanitize} 를 거친 문자열을 쓴다.</b> 한쪽만 정리하면
     * 두 조건이 서로 다른 것을 찾게 되어 <b>AND 결과가 항상 0건</b>이 된다.
     * 예를 들어 {@code "+++착장***"} 은 MATCH 쪽에서 "착장" 이 되는데,
     * LIKE 가 원문 그대로 {@code '%+++착장***%'} 를 찾으면 아무것도 걸리지 않는다.
     *
     * <p>와일드카드({@code % _})는 사용자 입력이 아니라 리터럴로 다룬다.
     * 이게 없으면 {@code %} 한 글자로 전체 조회가 된다.
     */
    public static String toLikePattern(String keyword) {
        String escaped = sanitize(keyword)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * 연산자 문자만 입력된 경우({@code "+++"}) 검색어로서 의미가 없다.
     * 이때 {@code LIKE '%%'} 로 내려가면 <b>필터가 아니라 전체 조회</b>가 되므로 호출부가 걸러낸다.
     */
    public static boolean isEmpty(String keyword) {
        return sanitize(keyword).isEmpty();
    }

    private static List<String> words(String keyword) {
        String sanitized = sanitize(keyword);
        if (sanitized.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(sanitized.split(" "));
    }
}
