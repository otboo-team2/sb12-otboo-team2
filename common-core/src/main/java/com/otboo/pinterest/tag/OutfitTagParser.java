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
 *
 * <h2>보드 description 과 나눠 달 수도 있다</h2>
 * {@link #parseMerged} 를 쓰면 보드에 {@code style·temp} 를, 핀에 {@code sky·gender} 를
 * 나눠 달아도 된다 — 핀마다 네 가지를 전부 적을 필요가 없다. {@link #parse} 는 그대로
 * "핀 하나의 description 에 전부 적는" 기존 방식이라 손대지 않았다.
 */
public final class OutfitTagParser {

    public static final String SENTINEL = "@otboo";

    private OutfitTagParser() {
    }

    public static OutfitTagParseResult parse(String description) {
        Extraction extraction = extract(description);
        if (!extraction.hasTagLine()) {
            return OutfitTagParseResult.untagged();
        }

        List<String> errors = new ArrayList<>(extraction.errors());
        OutfitTags tags = extraction.tags();
        requirePresent(extraction.seenKeys(), tags.temp(), "temp", errors);
        requirePresent(extraction.seenKeys(), tags.sky(), "sky", errors);
        requirePresent(extraction.seenKeys(), tags.gender(), "gender", errors);
        requirePresent(extraction.seenKeys(), tags.styles().isEmpty() ? null : tags.styles(), "style", errors);

        TagStatus status = errors.isEmpty() ? TagStatus.TAGGED : TagStatus.MALFORMED;
        return new OutfitTagParseResult(status, tags, errors);
    }

    /**
     * 보드 description 과 핀 description 을 각각 읽어 합친다.
     *
     * <p>보드에 {@code style·temp} 를, 핀에 {@code sky·gender} 를 나눠 다는 큐레이션을
     * 지원한다. 스칼라 값({@code temp·sky·gender})은 핀 쪽 값이 있으면 그걸 쓰고, 없으면
     * 보드 값으로 채운다. {@code style·item} 은 핀이 하나라도 지정했으면 핀 것만 쓴다 —
     * 이 핀만 보드와 다른 스타일을 주고 싶을 때 보드 값을 통째로 덮을 수 있게 하기 위해서다.
     *
     * <p>둘 다 {@code @otboo} 줄이 없으면 {@link TagStatus#UNTAGGED} 다. 하나라도 있는데
     * 합친 결과가 필수값을 못 채우면 {@link TagStatus#MALFORMED} 다.
     */
    public static OutfitTagParseResult parseMerged(String boardDescription, String pinDescription) {
        Extraction board = extract(boardDescription);
        Extraction pin = extract(pinDescription);
        if (!board.hasTagLine() && !pin.hasTagLine()) {
            return OutfitTagParseResult.untagged();
        }

        List<String> errors = new ArrayList<>();
        if (board.hasTagLine()) {
            // 보드도 태그를 다는 실제 "나눠 달기" 상황일 때만 출처를 표시한다. 보드에 태그가
            // 없으면(대다수) 핀이 혼자 다 적는 기존 방식과 같으니, 메시지 형식도 그대로 둔다.
            board.errors().forEach(error -> errors.add("[보드] " + error));
            pin.errors().forEach(error -> errors.add("[핀] " + error));
        } else {
            errors.addAll(pin.errors());
        }

        // 두 쪽 중 어느 하나라도 키를 봤으면 "봤다"로 친다 — 값이 틀려서 null 이 된 것과
        // 아예 안 적은 것을 구분하는 requirePresent 의 판단 기준을 합친 결과에도 그대로 쓴다.
        Set<String> seenKeys = new HashSet<>(board.seenKeys());
        seenKeys.addAll(pin.seenKeys());

        OutfitTags merged = merge(board.tags(), pin.tags());
        requirePresent(seenKeys, merged.temp(), "temp", errors);
        requirePresent(seenKeys, merged.sky(), "sky", errors);
        requirePresent(seenKeys, merged.gender(), "gender", errors);
        requirePresent(seenKeys, merged.styles().isEmpty() ? null : merged.styles(), "style", errors);

        TagStatus status = errors.isEmpty() ? TagStatus.TAGGED : TagStatus.MALFORMED;
        return new OutfitTagParseResult(status, merged, errors);
    }

    private static OutfitTags merge(OutfitTags board, OutfitTags pin) {
        TempBand temp = pin.temp() != null ? pin.temp() : board.temp();
        SkyTag sky = pin.sky() != null ? pin.sky() : board.sky();
        GenderTag gender = pin.gender() != null ? pin.gender() : board.gender();
        Set<StyleTag> styles = pin.styles().isEmpty() ? board.styles() : pin.styles();
        Set<ItemTag> items = pin.items().isEmpty() ? board.items() : pin.items();
        return new OutfitTags(temp, sky, styles, items, gender);
    }

    private static boolean isTagLine(String line) {
        if (!line.regionMatches(true, 0, SENTINEL, 0, SENTINEL.length())) {
            return false;
        }
        // "@otboo2" 같은 우연한 접두어는 태그 줄이 아니다
        return line.length() == SENTINEL.length() || Character.isWhitespace(line.charAt(SENTINEL.length()));
    }

    /** description 하나에서 {@code @otboo} 줄을 찾아 값만 뽑는다. 필수 키 검사는 호출부가 한다. */
    private static Extraction extract(String description) {
        if (description == null) {
            return Extraction.EMPTY;
        }
        List<String> tagLines = description.lines()
                .map(String::strip)
                .filter(OutfitTagParser::isTagLine)
                .toList();
        if (tagLines.isEmpty()) {
            return Extraction.EMPTY;
        }

        List<String> errors = new ArrayList<>();
        if (tagLines.size() > 1) {
            // 어느 줄이 맞는지 추측하지 않는다. 틀린 쪽을 골라 조용히 잘못 분류하는 것보다 드러내는 게 낫다.
            errors.add("@otboo 줄이 %d개다. 하나만 남겨야 한다".formatted(tagLines.size()));
        }

        TempBand temp = null;
        SkyTag sky = null;
        GenderTag gender = null;
        Set<StyleTag> styles = EnumSet.noneOf(StyleTag.class);
        Set<ItemTag> items = EnumSet.noneOf(ItemTag.class);
        Set<String> seenKeys = new HashSet<>();

        String body = tagLines.getFirst().substring(SENTINEL.length()).strip();
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

        OutfitTags tags = new OutfitTags(temp, sky, styles, items, gender);
        return new Extraction(true, tags, seenKeys, errors);
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
        return found.isEmpty() ? null : found.getFirst();
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

    /** description 하나에서 뽑아낸 값. 필수 키 검증 전 상태다. */
    private record Extraction(boolean hasTagLine, OutfitTags tags, Set<String> seenKeys, List<String> errors) {

        static final Extraction EMPTY = new Extraction(false, OutfitTags.EMPTY, Set.of(), List.of());
    }
}
