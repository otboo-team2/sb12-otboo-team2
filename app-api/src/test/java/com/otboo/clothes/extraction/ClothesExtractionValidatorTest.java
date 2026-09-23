package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.dto.ClothesExtractionDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClothesExtractionValidatorTest {

    private static final UUID FIT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID THICKNESS_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SEASON_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID COLOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final URI PRODUCT_URL = URI.create("https://shop.example.com/products/1");

    private ClothesExtractionValidator validator;
    private ProductPageData page;
    private List<AttributeDefinitionSnapshot> catalog;

    @BeforeEach
    void setUp() {
        validator = new ClothesExtractionValidator();
        page = new ProductPageData(
                PRODUCT_URL,
                "페이지 상품명",
                "상품 설명",
                URI.create("https://cdn.example.com/main.jpg"),
                List.of(),
                List.of());
        catalog = List.of(
                new AttributeDefinitionSnapshot(FIT_ID, "핏", List.of("세미와이드", "와이드")),
                new AttributeDefinitionSnapshot(THICKNESS_ID, "두께감", List.of("얇음", "보통", "두꺼움")),
                new AttributeDefinitionSnapshot(SEASON_ID, "계절", List.of("여름", "겨울")),
                new AttributeDefinitionSnapshot(COLOR_ID, "색상", List.of("블랙", "그레이")));
    }

    @Test
    void mapsValidCandidateToRealDefinitionName() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("세미 와이드 데님", "BOTTOM", List.of(
                        attribute(FIT_ID, "세미와이드", " 세미 와이드 핏 ", "PAGE_TEXT", false))),
                catalog,
                "https://cdn.example.com/main.jpg");

        assertThat(result.name()).isEqualTo("세미 와이드 데님");
        assertThat(result.type()).isEqualTo(ClothesType.BOTTOM);
        assertThat(result.attributes()).singleElement().satisfies(attribute -> {
            assertThat(attribute.definitionId()).isEqualTo(FIT_ID);
            assertThat(attribute.definitionName()).isEqualTo("핏");
            assertThat(attribute.value()).isEqualTo("세미와이드");
            assertThat(attribute.evidence()).isEqualTo("세미 와이드 핏");
        });
    }

    @Test
    void removesUnknownDefinitionAndAddsFailure() {
        UUID unknown = UUID.randomUUID();

        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(attribute(
                        unknown, "값", "근거", "PAGE_TEXT", false))),
                catalog,
                null);

        assertThat(result.attributes()).isEmpty();
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure.field()).isEqualTo("attributes"));
    }

    @Test
    void removesValueAbsentFromDefinition() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(attribute(
                        FIT_ID, "슬림", "슬림 핏", "PAGE_TEXT", false))),
                catalog,
                null);

        assertThat(result.attributes()).isEmpty();
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure.field()).isEqualTo("attributes"));
    }

    @Test
    void removesBlankEvidence() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(attribute(
                        FIT_ID, "세미와이드", "  ", "PAGE_TEXT", false))),
                catalog,
                null);

        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void removesUnknownSource() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(attribute(
                        FIT_ID, "세미와이드", "세미와이드 핏", "UNKNOWN", false))),
                catalog,
                null);

        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void removesOptionDependentValueWithAmbiguityFailure() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(attribute(
                        FIT_ID, "세미와이드", "색상 옵션에 따라 다름", "PAGE_TEXT", true))),
                catalog,
                null);

        assertThat(result.attributes()).isEmpty();
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure.field()).isEqualTo("ambiguity"));
    }

    @Test
    void removesDuplicateAmbiguityFailures() {
        GeminiExtractionCandidate candidate = new GeminiExtractionCandidate(
                "상품",
                "TOP",
                List.of(),
                List.of("색상 옵션이 여러 개입니다.", "소재를 확인할 수 없습니다.", "핏이 불분명합니다."));

        ClothesExtractionDto result = validator.validate(page, candidate, catalog, null);

        assertThat(result.failures())
                .filteredOn(failure -> failure.field().equals("ambiguity"))
                .singleElement();
    }

    @Test
    void removesConflictingValuesForSameDefinition() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(
                        attribute(FIT_ID, "세미와이드", "세미 와이드", "PAGE_TEXT", false),
                        attribute(FIT_ID, "와이드", "와이드 핏", "DETAIL_IMAGE", false))),
                catalog,
                null);

        assertThat(result.attributes()).isEmpty();
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure.field()).isEqualTo("attributes"));
    }

    @Test
    void doesNotInferThinFromLightFabricAlone() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(attribute(
                        THICKNESS_ID, "얇음", "가벼운 원단", "PAGE_TEXT", false))),
                catalog,
                null);

        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void doesNotInferSummerFromCoolWearingAlone() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(attribute(
                        SEASON_ID, "여름", "시원하게 착용", "PAGE_TEXT", false))),
                catalog,
                null);

        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void rejectsSimilarColorThatDoesNotMatchEvidence() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "BOTTOM", List.of(attribute(
                        COLOR_ID, "블랙", "Color- charcoal", "PAGE_TEXT", false))),
                catalog,
                null);

        assertThat(result.attributes()).isEmpty();
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure.field()).isEqualTo("attributes"));
    }

    @Test
    void acceptsTranslatedColorThatMatchesEvidence() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(attribute(
                        COLOR_ID, "블랙", "Color- black", "PAGE_TEXT", false))),
                catalog,
                null);

        assertThat(result.attributes()).singleElement().satisfies(attribute -> {
            assertThat(attribute.definitionName()).isEqualTo("색상");
            assertThat(attribute.value()).isEqualTo("블랙");
        });
    }

    @Test
    void invalidTypeBecomesNullAndAddsFailure() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "NOT_A_TYPE", List.of()),
                catalog,
                null);

        assertThat(result.type()).isNull();
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure.field()).isEqualTo("type"));
    }

    @Test
    void blankGeminiNameFallsBackToPageName() {
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("  ", "TOP", List.of()),
                catalog,
                null);

        assertThat(result.name()).isEqualTo("페이지 상품명");
    }

    @Test
    void trimsAndCapsEvidence() {
        String evidence = "  " + "가".repeat(250) + "  ";
        ClothesExtractionDto result = validator.validate(
                page,
                candidate("상품", "TOP", List.of(attribute(
                        FIT_ID, "세미와이드", evidence, "PAGE_TEXT", false))),
                catalog,
                null);

        assertThat(result.attributes()).singleElement()
                .extracting(attribute -> attribute.evidence())
                .isEqualTo("가".repeat(200));
    }

    @Test
    void rejectsWhenNoUsableProductDataRemains() {
        ProductPageData emptyPage = new ProductPageData(PRODUCT_URL, " ", " ", null, List.of(), List.of());

        assertThatThrownBy(() -> validator.validate(
                emptyPage,
                candidate(" ", "INVALID", List.of()),
                catalog,
                null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ClothesErrorCode.PRODUCT_DATA_NOT_FOUND));
    }

    private GeminiExtractionCandidate candidate(
            String name,
            String type,
            List<GeminiExtractionCandidate.AttributeCandidate> attributes
    ) {
        return new GeminiExtractionCandidate(name, type, attributes, List.of());
    }

    private GeminiExtractionCandidate.AttributeCandidate attribute(
            UUID definitionId,
            String value,
            String evidence,
            String source,
            boolean optionDependent
    ) {
        return new GeminiExtractionCandidate.AttributeCandidate(
                definitionId.toString(), value, evidence, source, optionDependent);
    }
}
