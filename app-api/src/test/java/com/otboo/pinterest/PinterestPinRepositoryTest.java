package com.otboo.pinterest;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.pinterest.entity.PinterestPin;
import com.otboo.pinterest.repository.PinterestPinRepository;
import com.otboo.pinterest.tag.OutfitTagParser;
import com.otboo.pinterest.tag.TagStatus;
import com.otboo.pinterest.tag.TempBand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V6 마이그레이션과 엔티티 매핑이 실제 MySQL에서 맞는지 확인한다.
 * {@code ddl-auto: validate} 라 컬럼 타입이 어긋나면 컨텍스트가 뜨지 않는다.
 */
class PinterestPinRepositoryTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");

    @Autowired PinterestPinRepository repository;
    @Autowired TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("핀과 태그를 저장하고 다시 읽는다")
    void savesPinWithTags() {
        String description = "@otboo temp:5-8 sky:cloudy style:minimal,street item:knit gender:unisex";
        repository.save(pin("100", description));

        transaction.executeWithoutResult(status -> {
            PinterestPin found = repository.findByPinId("100").orElseThrow();
            assertThat(found.getTagStatus()).isEqualTo(TagStatus.TAGGED);
            assertThat(found.getTempBand()).isEqualTo(TempBand.T5_8);
            assertThat(found.getTags()).hasSize(3);
        });
    }

    @Test
    @DisplayName("재동기화로 태그가 일부만 바뀌어도 유니크 제약에 걸리지 않는다")
    void refreshWithOverlappingTags() {
        repository.save(pin("200", "@otboo temp:5-8 sky:cloudy style:minimal,street gender:unisex"));

        String edited = "@otboo temp:9-11 sky:cloudy style:minimal,classic item:coat gender:unisex";
        transaction.executeWithoutResult(status -> {
            PinterestPin found = repository.findByPinId("200").orElseThrow();
            found.refresh("1", "https://i.pinimg.com/b.jpg", null, null, edited, OutfitTagParser.parse(edited), NOW);
            repository.flush();
        });

        transaction.executeWithoutResult(status -> {
            PinterestPin found = repository.findByPinId("200").orElseThrow();
            assertThat(found.getTempBand()).isEqualTo(TempBand.T9_11);
            assertThat(found.getTags())
                    .extracting(tag -> tag.getTagType() + ":" + tag.getTagValue())
                    .containsExactlyInAnyOrder("STYLE:MINIMAL", "STYLE:CLASSIC", "ITEM:COAT");
        });
    }

    @Test
    @DisplayName("태그가 틀리거나 없는 핀도 상태와 오류를 담아 저장된다")
    void savesMalformedAndUntaggedPins() {
        repository.save(pin("300", "@otboo temp:5~8 sky:cloudy style:minimal gender:unisex"));
        repository.save(pin("301", "태그 없는 핀"));

        List<PinterestPin> found = repository.findAllByPinIdIn(List.of("300", "301", "없는핀"));

        assertThat(found).extracting(PinterestPin::getTagStatus)
                .containsExactlyInAnyOrder(TagStatus.MALFORMED, TagStatus.UNTAGGED);
        assertThat(found).filteredOn(pin -> pin.getPinId().equals("300"))
                .singleElement()
                .extracting(PinterestPin::getTagErrors)
                .isEqualTo("'temp' 에 쓸 수 없는 값: 5~8");
    }

    private static PinterestPin pin(String pinId, String description) {
        return PinterestPin.create(pinId, "1", "https://i.pinimg.com/a.jpg", "https://shop.example.com",
                "제목", description, OutfitTagParser.parse(description), NOW);
    }
}
