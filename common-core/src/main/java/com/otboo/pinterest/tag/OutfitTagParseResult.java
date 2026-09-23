package com.otboo.pinterest.tag;

import java.util.List;

/**
 * 파싱 결과. 예외 대신 결과로 돌려준다.
 *
 * <p>동기화 배치는 핀 수백 개를 한 번에 읽는다. 오류를 예외로 던지면 핀 하나의 오타가
 * 배치 전체를 멈추거나, 잡아서 삼키는 코드가 곳곳에 생긴다. 결과로 받으면 배치는
 * 상태를 그대로 저장하고 오류 목록을 로그로 남기기만 하면 된다.
 */
public record OutfitTagParseResult(TagStatus status, OutfitTags tags, List<String> errors) {

    public OutfitTagParseResult {
        tags = tags == null ? OutfitTags.EMPTY : tags;
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    static OutfitTagParseResult untagged() {
        return new OutfitTagParseResult(TagStatus.UNTAGGED, OutfitTags.EMPTY, List.of());
    }

    public boolean isTagged() {
        return status == TagStatus.TAGGED;
    }
}
