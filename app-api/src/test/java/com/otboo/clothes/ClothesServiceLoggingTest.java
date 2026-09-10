package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.clothes.repository.ClothesAttributeSelectableValueRepository;
import com.otboo.clothes.repository.ClothesAttributeValueRepository;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.storage.ImageStorage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ClothesServiceLoggingTest {

    @Mock
    ClothesRepository clothesRepository;

    @Mock
    ClothesAttributeDefinitionRepository definitionRepository;

    @Mock
    ClothesAttributeSelectableValueRepository selectableValueRepository;

    @Mock
    ClothesAttributeValueRepository attributeValueRepository;

    @Mock
    ImageStorage imageStorage;

    @InjectMocks
    ClothesService clothesService;

    @Test
    @DisplayName("이미지 삭제 실패 로그는 표준 이벤트를 사용하고 이미지 URL을 노출하지 않는다")
    void logsImageDeleteFailureWithoutImageUrl() {
        UUID ownerId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        String imageUrl = "/images/clothes/shirt.jpg?token=secret";
        Clothes clothes = Clothes.create(ownerId, "이미지 티셔츠", ClothesType.TOP, imageUrl);
        given(clothesRepository.findById(clothesId)).willReturn(Optional.of(clothes));
        willThrow(new IllegalStateException("delete failed"))
                .given(imageStorage).delete(imageUrl);
        Logger logger = (Logger) LoggerFactory.getLogger(ClothesService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            clothesService.delete(ownerId, clothesId);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(messages).contains("image_delete_failed");
        assertThat(messages).allSatisfy(message -> assertThat(message)
                .doesNotContain(imageUrl, "token=secret"));
    }
}
