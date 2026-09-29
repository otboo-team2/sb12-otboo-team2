package com.otboo.pinterest.tag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OutfitTagParserTest {

    @Test
    @DisplayName("규약대로 적힌 description 을 태그로 읽는다")
    void parsesWellFormedLine() {
        OutfitTagParseResult result = OutfitTagParser.parse("""
                겨울 레이어드 니트 — 톤온톤 베이지

                @otboo temp:5-8 sky:cloudy style:minimal,street item:knit,coat gender:unisex
                """);

        assertThat(result.status()).isEqualTo(TagStatus.TAGGED);
        assertThat(result.errors()).isEmpty();
        assertThat(result.tags().temp()).isEqualTo(TempBand.T5_8);
        assertThat(result.tags().sky()).isEqualTo(SkyTag.CLOUDY);
        assertThat(result.tags().styles()).containsExactlyInAnyOrder(StyleTag.MINIMAL, StyleTag.STREET);
        assertThat(result.tags().items()).containsExactlyInAnyOrder(ItemTag.KNIT, ItemTag.COAT);
        assertThat(result.tags().gender()).isEqualTo(GenderTag.UNISEX);
    }

    @Test
    @DisplayName("item 은 선택이라 없어도 TAGGED 다")
    void itemIsOptional() {
        OutfitTagParseResult result = OutfitTagParser.parse("@otboo temp:28up sky:clear style:casual gender:women");

        assertThat(result.status()).isEqualTo(TagStatus.TAGGED);
        assertThat(result.tags().items()).isEmpty();
    }

    @Test
    @DisplayName("휴대폰 자동 대문자 · 여분 공백 · 쉼표 뒤 공백 없는 값도 읽는다")
    void toleratesCaseAndWhitespace() {
        OutfitTagParseResult result = OutfitTagParser.parse(
                "   @OTBOO   Temp:5-8   Sky:Cloudy  Style:Minimal,,STREET,  Gender:UNISEX  ");

        assertThat(result.status()).isEqualTo(TagStatus.TAGGED);
        assertThat(result.tags().styles()).containsExactlyInAnyOrder(StyleTag.MINIMAL, StyleTag.STREET);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"그냥 예쁜 코디", "#minimal #street", "@otboo2 temp:5-8"})
    @DisplayName("@otboo 줄이 없으면 오류가 아니라 UNTAGGED 다")
    void untaggedWhenNoSentinel(String description) {
        OutfitTagParseResult result = OutfitTagParser.parse(description);

        assertThat(result.status()).isEqualTo(TagStatus.UNTAGGED);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("모르는 값이 있어도 끝까지 읽고, 읽어낸 값은 남긴다")
    void collectsAllErrorsAndKeepsValidValues() {
        OutfitTagParseResult result = OutfitTagParser.parse(
                "@otboo temp:5~8 sky:cloudy style:minimalist,street gender:unisex");

        assertThat(result.status()).isEqualTo(TagStatus.MALFORMED);
        assertThat(result.errors()).containsExactlyInAnyOrder(
                "'temp' 에 쓸 수 없는 값: 5~8",
                "'style' 에 쓸 수 없는 값: minimalist");
        assertThat(result.tags().sky()).isEqualTo(SkyTag.CLOUDY);
        assertThat(result.tags().styles()).containsExactly(StyleTag.STREET);
    }

    @Test
    @DisplayName("필수 키가 없으면 MALFORMED 다")
    void missingRequiredKeys() {
        OutfitTagParseResult result = OutfitTagParser.parse("@otboo temp:5-8 style:minimal");

        assertThat(result.status()).isEqualTo(TagStatus.MALFORMED);
        assertThat(result.errors()).containsExactlyInAnyOrder("필수 키가 없다: sky", "필수 키가 없다: gender");
    }

    @Test
    @DisplayName("키만 있고 값이 비어 있으면 누락과 구분해서 알린다")
    void emptyValue() {
        OutfitTagParseResult result = OutfitTagParser.parse("@otboo temp:5-8 sky: style:minimal gender:men");

        assertThat(result.errors()).containsExactly("'sky' 에 값이 없다");
    }

    @Test
    @DisplayName("값이 하나여야 하는 키에 여러 개를 적으면 MALFORMED 다")
    void singleValuedKeyWithMultipleValues() {
        OutfitTagParseResult result = OutfitTagParser.parse(
                "@otboo temp:5-8,9-11 sky:clear style:minimal gender:men");

        assertThat(result.errors()).containsExactly("'temp' 는 값을 하나만 가진다: 5-8,9-11");
        assertThat(result.tags().temp()).isNull();
    }

    @Test
    @DisplayName("같은 키가 두 번 · 모르는 키 · 형식이 틀린 토큰을 모두 알린다")
    void structuralErrors() {
        OutfitTagParseResult result = OutfitTagParser.parse(
                "@otboo temp:5-8 temp:9-11 sky:clear style:minimal gender:men season:winter minimal");

        assertThat(result.errors()).containsExactlyInAnyOrder(
                "'temp' 가 두 번 나온다",
                "모르는 키: season",
                "'키:값' 형식이 아니다: minimal");
        assertThat(result.tags().temp()).isEqualTo(TempBand.T5_8);
    }

    @Test
    @DisplayName("@otboo 줄이 여러 개면 추측하지 않고 오류로 알린다")
    void multipleTagLines() {
        OutfitTagParseResult result = OutfitTagParser.parse("""
                @otboo temp:5-8 sky:clear style:minimal gender:men
                @otboo temp:9-11 sky:clear style:minimal gender:men
                """);

        assertThat(result.status()).isEqualTo(TagStatus.MALFORMED);
        assertThat(result.errors()).containsExactly("@otboo 줄이 2개다. 하나만 남겨야 한다");
    }

    @Test
    @DisplayName("[병합] 보드가 style·temp 를, 핀이 sky·gender 를 주면 합쳐서 TAGGED 다")
    void mergedTagsFromBoardAndPin() {
        OutfitTagParseResult result = OutfitTagParser.parseMerged(
                "@otboo style:minimal temp:5-8",
                "@otboo sky:cloudy gender:unisex");

        assertThat(result.status()).isEqualTo(TagStatus.TAGGED);
        assertThat(result.errors()).isEmpty();
        assertThat(result.tags().temp()).isEqualTo(TempBand.T5_8);
        assertThat(result.tags().sky()).isEqualTo(SkyTag.CLOUDY);
        assertThat(result.tags().gender()).isEqualTo(GenderTag.UNISEX);
        assertThat(result.tags().styles()).containsExactly(StyleTag.MINIMAL);
    }

    @Test
    @DisplayName("[병합] 핀이 자기만의 style 을 주면 보드 style 을 통째로 덮는다")
    void pinStyleOverridesBoardStyle() {
        OutfitTagParseResult result = OutfitTagParser.parseMerged(
                "@otboo style:minimal temp:5-8",
                "@otboo sky:cloudy gender:unisex style:street");

        assertThat(result.tags().styles()).containsExactly(StyleTag.STREET);
        assertThat(result.tags().temp()).isEqualTo(TempBand.T5_8); // temp 는 핀이 안 줬으니 보드 값 그대로
    }

    @Test
    @DisplayName("[병합] 보드에 태그가 없으면(대다수 보드) 핀 혼자 적는 기존 parse() 와 결과·메시지가 같다")
    void mergedFallsBackToPinOnlyBehaviorWhenBoardHasNoTagLine() {
        String pinDescription = "@otboo temp:5~8 sky:cloudy style:minimal gender:unisex";

        OutfitTagParseResult merged = OutfitTagParser.parseMerged(null, pinDescription);
        OutfitTagParseResult plain = OutfitTagParser.parse(pinDescription);

        assertThat(merged.status()).isEqualTo(plain.status());
        assertThat(merged.errors()).isEqualTo(plain.errors()); // "[핀]" 접두어 없이 그대로
        assertThat(merged.tags()).isEqualTo(plain.tags());
    }

    @Test
    @DisplayName("[병합] 둘 다 @otboo 줄이 없으면 UNTAGGED 다")
    void mergedUntaggedWhenNeitherHasTagLine() {
        OutfitTagParseResult result = OutfitTagParser.parseMerged("그냥 보드 설명", "그냥 핀 설명");

        assertThat(result.status()).isEqualTo(TagStatus.UNTAGGED);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("[병합] 보드가 style·temp 를 줘도 핀이 sky·gender 를 안 주면 여전히 MALFORMED 다")
    void mergedStillRequiresPinOwnTags() {
        OutfitTagParseResult result = OutfitTagParser.parseMerged(
                "@otboo style:minimal temp:5-8",
                "그냥 핀 설명, 태그 없음");

        assertThat(result.status()).isEqualTo(TagStatus.MALFORMED);
        assertThat(result.errors()).containsExactlyInAnyOrder(
                "필수 키가 없다: sky",
                "필수 키가 없다: gender");
    }

    @Test
    @DisplayName("[병합] 값이 틀린 쪽은 출처(보드/핀)를 접두어로 구분해서 알린다")
    void mergedErrorsArePrefixedBySource() {
        OutfitTagParseResult result = OutfitTagParser.parseMerged(
                "@otboo style:minimal temp:이상한값",
                "@otboo sky:cloudy gender:unisex");

        assertThat(result.status()).isEqualTo(TagStatus.MALFORMED);
        assertThat(result.errors()).containsExactly("[보드] 'temp' 에 쓸 수 없는 값: 이상한값");
    }
}
