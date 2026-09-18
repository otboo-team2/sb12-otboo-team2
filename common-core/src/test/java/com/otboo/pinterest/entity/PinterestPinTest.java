package com.otboo.pinterest.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.pinterest.tag.OutfitTagParser;
import com.otboo.pinterest.tag.TagStatus;
import com.otboo.pinterest.tag.TempBand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PinterestPinTest {

    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");

    @Test
    @DisplayName("파싱 결과를 컬럼과 자식 태그로 옮긴다")
    void createsFromParsedTags() {
        PinterestPin pin = create("@otboo temp:5-8 sky:cloudy style:minimal,street item:knit gender:unisex");

        assertThat(pin.getTagStatus()).isEqualTo(TagStatus.TAGGED);
        assertThat(pin.getTempBand()).isEqualTo(TempBand.T5_8);
        assertThat(pin.getTagErrors()).isNull();
        assertThat(tagsOf(pin)).containsExactlyInAnyOrder("STYLE:MINIMAL", "STYLE:STREET", "ITEM:KNIT");
    }

    @Test
    @DisplayName("재동기화하면 남는 태그 객체는 그대로 두고 달라진 것만 바꾼다")
    void refreshKeepsUnchangedTagInstances() {
        PinterestPin pin = create("@otboo temp:5-8 sky:cloudy style:minimal,street gender:unisex");
        PinterestPinTag minimal = pin.getTags().stream()
                .filter(tag -> tag.getTagValue().equals("MINIMAL"))
                .findFirst().orElseThrow();

        String edited = "@otboo temp:5-8 sky:cloudy style:minimal,classic gender:unisex";
        pin.refresh("board", "https://i.pinimg.com/a.jpg", null, null, edited, OutfitTagParser.parse(edited), NOW);

        assertThat(tagsOf(pin)).containsExactlyInAnyOrder("STYLE:MINIMAL", "STYLE:CLASSIC");
        // 같은 객체가 남아야 DELETE 후 INSERT 가 아니게 되어 유니크 제약에 걸리지 않는다
        assertThat(pin.getTags()).anyMatch(tag -> tag == minimal);
    }

    @Test
    @DisplayName("오류는 줄바꿈으로 이어 저장하고 컬럼 길이를 넘지 않는다")
    void storesTruncatedErrors() {
        String longValue = "x".repeat(1200);
        PinterestPin pin = create("@otboo temp:" + longValue + " sky:clear style:minimal gender:men");

        assertThat(pin.getTagStatus()).isEqualTo(TagStatus.MALFORMED);
        assertThat(pin.getTagErrors()).hasSize(PinterestPin.MAX_TAG_ERRORS_LENGTH).endsWith("…");
    }

    @Test
    @DisplayName("태그를 모두 지운 description 으로 바뀌면 자식 태그도 비운다")
    void clearsTagsWhenUntagged() {
        PinterestPin pin = create("@otboo temp:5-8 sky:cloudy style:minimal gender:unisex");

        pin.refresh("board", "https://i.pinimg.com/a.jpg", null, null, "태그 없음",
                OutfitTagParser.parse("태그 없음"), NOW);

        assertThat(pin.getTagStatus()).isEqualTo(TagStatus.UNTAGGED);
        assertThat(pin.getTempBand()).isNull();
        assertThat(pin.getTags()).isEmpty();
    }

    private static PinterestPin create(String description) {
        return PinterestPin.create("123", "board", "https://i.pinimg.com/a.jpg", null, null,
                description, OutfitTagParser.parse(description), NOW);
    }

    private static List<String> tagsOf(PinterestPin pin) {
        return pin.getTags().stream().map(tag -> tag.getTagType() + ":" + tag.getTagValue()).toList();
    }
}
