package com.otboo.pinterest.tag;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 핀 description 에서 {@code @otboo} 줄을 읽어 태그로 바꾼다.
 *
 * <pre>
 * 겨울 레이어드 니트 — 톤온톤 베이지
 *
 * &#64;otboo temp:5-8 sky:cloudy style:minimal,street item:knit,coat gender:unisex
 * </pre>
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li>{@code @otboo} 로 시작하는 줄 하나만 읽는다. 윗줄의 자유 설명은 건드리지 않는다</li>
 *   <li>{@code 키:값} 을 공백으로 구분한다. 값이 여러 개면 쉼표로 잇는다</li>
 *   <li>대소문자는 가리지 않는다. 휴대폰 자동 대문자 때문에 {@code Style:Minimal} 이 흔하다</li>
 *   <li>필수: {@code temp} · {@code sky} · {@code style} · {@code gender}. 선택: {@code item}</li>
 * </ul>
 *
 * <h2>틀린 값이 있어도 끝까지 읽는다</h2>
 * 오류를 전부 모아서 돌려준다. 첫 오류에서 멈추면 큐레이터가 한 번 고칠 때마다
 * 다음 오류가 하나씩 드러나 여러 번 왕복하게 된다.
 */
public final class OutfitTagParser {

    public static final String SENTINEL = "@otboo";

    private OutfitTagParser() {
    }

    public static OutfitTagParseResult parse(String description) {
        if (description == null) {
            return OutfitTagParseResult.untagged();
        }
        List<String> tagLines = description.lines()
                .map(String::strip)
                .filter(OutfitTagParser::isTagLine)
                .toList();
        if (tagLines.isEmpty()) {
            return OutfitTagParseResult.untagged();
        }

        List<String> errors = new ArrayList<>();
        if (tagLines.size() > 1) {
            // 어느 줄이 맞는지 추측하지 않는다. 틀린 쪽을 골라 조용히 잘못 분류하는 것보다 드러내는 게 낫다.
            errors.add("@otboo 줄이 %d개다. 하나만 남겨야 한다".formatted(tagLines.size()));
        }
        return parseLine(tagLines.get(0), errors);
    }

    private static boolean isTagLine(String line) {
        if (!line.regionMatches(true, 0, SENTINEL, 0, SENTINEL.length())) {
            return false;
        }
        // "@otboo2" 같은 우연한 접두어는 태그 줄이 아니다
        return line.length() == SENTINEL.length() || Character.isWhitespace(line.charAt(SENTINEL.length()));
    }

    private static OutfitTagParseResult parseLine(String line, List<String> errors) {
        TempBand temp = null;
        SkyTag sky = null;
        GenderTag gender = null;
        Set<StyleTag> styles = EnumSet.noneOf(StyleTag.class);
        Set<ItemTag> items = EnumSet.noneOf(ItemTag.class);
        Set<String> seenKeys = new HashSet<>();

        String body = line.substring(SENTINEL.length()).strip();
        for (String token : body.isEmpty() ? new String[0] : body.split("\\s+")) {
            int colon = token.indexOf(':');
            if (colon <= 0) {
                errors.add("'키:값' 형식이 아니다: " + token);
                continue;
            }
            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            List<String> values = splitValues(token.substring(colon + 1));
            if (!seenKeys.add(key)) {
                errors.add("'%s' 가 두 번 나온다".formatted(key));
                continue;
            }

            switch (key) {
                case "temp" -> temp = single(TempBand.class, key, values, errors);
                case "sky" -> sky = single(SkyTag.class, key, values, errors);
                case "gender" -> gender = single(GenderTag.class, key, values, errors);
                case "style" -> styles.addAll(multiple(StyleTag.class, key, values, errors));
                case "item" -> items.addAll(multiple(ItemTag.class, key, values, errors));
                default -> errors.add("모르는 키: " + key);
            }
        }

        requirePresent(seenKeys, temp, "temp", errors);
        requirePresent(seenKeys, sky, "sky", errors);
        requirePresent(seenKeys, gender, "gender", errors);
        requirePresent(seenKeys, styles.isEmpty() ? null : styles, "style", errors);

        OutfitTags tags = new OutfitTags(temp, sky, styles, items, gender);
        TagStatus status = errors.isEmpty() ? TagStatus.TAGGED : TagStatus.MALFORMED;
        return new OutfitTagParseResult(status, tags, errors);
    }

    private static List<String> splitValues(String raw) {
        List<String> values = new ArrayList<>();
        for (String value : raw.split(",")) {
            String normalized = value.strip().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private static <E extends Enum<E> & TagValue> E single(
            Class<E> type, String key, List<String> values, List<String> errors) {
        if (values.size() > 1) {
            errors.add("'%s' 는 값을 하나만 가진다: %s".formatted(key, String.join(",", values)));
            return null;
        }
        List<E> found = multiple(type, key, values, errors);
        return found.isEmpty() ? null : found.get(0);
    }

    private static <E extends Enum<E> & TagValue> List<E> multiple(
            Class<E> type, String key, List<String> values, List<String> errors) {
        List<E> found = new ArrayList<>();
        for (String value : values) {
            Optional<E> constant = TagValue.find(type, value);
            if (constant.isPresent()) {
                found.add(constant.get());
            } else {
                errors.add("'%s' 에 쓸 수 없는 값: %s".formatted(key, value));
            }
        }
        return found;
    }

    /** 키 자체가 없을 때만 "필수값 누락"으로 알린다. 키는 있는데 값이 틀린 경우는 이미 오류가 쌓여 있다. */
    private static void requirePresent(Set<String> seenKeys, Object parsed, String key, List<String> errors) {
        if (parsed == null && !seenKeys.contains(key)) {
            errors.add("필수 키가 없다: " + key);
        } else if (parsed == null && errors.stream().noneMatch(error -> error.contains("'" + key + "'"))) {
            errors.add("'%s' 에 값이 없다".formatted(key));
        }
    }
}
