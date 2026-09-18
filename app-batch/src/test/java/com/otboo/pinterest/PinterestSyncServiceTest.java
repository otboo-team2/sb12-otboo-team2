package com.otboo.pinterest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otboo.pinterest.PinterestSyncService.SyncResult;
import com.otboo.pinterest.client.PinterestClient;
import com.otboo.pinterest.client.PinterestPageResponse;
import com.otboo.pinterest.client.PinterestPinResponse;
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
import org.mockito.ArgumentCaptor;

class PinterestSyncServiceTest {

    private static final String BOARD_ID = "549755885175";
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private static final String TAGGED = "@otboo temp:5-8 sky:cloudy style:minimal item:knit gender:unisex";

    private PinterestClient client;
    private PinterestPinRepository repository;
    private PinterestSyncService service;

    @BeforeEach
    void setUp() {
        client = mock(PinterestClient.class);
        repository = mock(PinterestPinRepository.class);
        when(repository.findAllByPinIdIn(any())).thenReturn(List.of());
        service = new PinterestSyncService(client, repository);
    }

    @Test
    @DisplayName("bookmark 를 따라 다음 페이지까지 받고 마지막 페이지에서 멈춘다")
    void followsBookmarkUntilLastPage() {
        when(client.listBoardPins(BOARD_ID, null))
                .thenReturn(new PinterestPageResponse<>(List.of(pin("1", TAGGED)), "bookmark-2"));
        when(client.listBoardPins(BOARD_ID, "bookmark-2"))
                .thenReturn(new PinterestPageResponse<>(List.of(pin("2", TAGGED)), null));

        SyncResult result = service.syncBoard(BOARD_ID, NOW);

        verify(client).listBoardPins(BOARD_ID, null);
        verify(client).listBoardPins(BOARD_ID, "bookmark-2");
        verify(client, never()).listBoardPins(eq(BOARD_ID), eq("bookmark-3"));
        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.upserted()).isEqualTo(2);
    }

    @Test
    @DisplayName("bookmark 가 끝나지 않아도 페이지 상한에서 멈춘다 — 하루 호출 한도를 한 보드가 먹지 않게")
    void stopsAtPageLimit() {
        when(client.listBoardPins(eq(BOARD_ID), isNull()))
                .thenReturn(new PinterestPageResponse<>(List.of(pin("1", TAGGED)), "next"));
        when(client.listBoardPins(eq(BOARD_ID), eq("next")))
                .thenReturn(new PinterestPageResponse<>(List.of(pin("1", TAGGED)), "next"));

        service.syncBoard(BOARD_ID, NOW);

        verify(client, times(PinterestSyncService.MAX_PAGES_PER_BOARD))
                .listBoardPins(eq(BOARD_ID), any());
    }

    @Test
    @DisplayName("이미지가 없는 핀(영상 · 여러 장)과 ID 가 없는 핀은 건너뛴다")
    void skipsPinsWithoutImageOrId() {
        PinterestPinResponse video = new PinterestPinResponse("10", BOARD_ID, null, null, TAGGED, null,
                new PinterestPinResponse.Media("video", null));
        PinterestPinResponse noId = new PinterestPinResponse(" ", BOARD_ID, null, null, TAGGED, null, media());
        when(client.listBoardPins(BOARD_ID, null))
                .thenReturn(new PinterestPageResponse<>(List.of(pin("11", TAGGED), video, noId), null));

        SyncResult result = service.syncBoard(BOARD_ID, NOW);

        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(savedPins()).extracting(PinterestPin::getPinId).containsExactly("11");
    }

    @Test
    @DisplayName("이미 있는 핀은 새로 만들지 않고 같은 행을 갱신한다")
    void refreshesExistingPinInsteadOfInserting() {
        PinterestPin existing = PinterestPin.create("20", BOARD_ID, "https://i.pinimg.com/old.jpg", null, null,
                "@otboo temp:23-27 sky:clear style:casual gender:women",
                OutfitTagParser.parse("@otboo temp:23-27 sky:clear style:casual gender:women"), NOW);
        when(repository.findAllByPinIdIn(List.of("20"))).thenReturn(List.of(existing));
        when(client.listBoardPins(BOARD_ID, null))
                .thenReturn(new PinterestPageResponse<>(List.of(pin("20", TAGGED)), null));

        service.syncBoard(BOARD_ID, NOW);

        assertThat(savedPins()).containsExactly(existing);
        assertThat(existing.getTempBand()).isEqualTo(TempBand.T5_8);
        assertThat(existing.getImageUrl()).isEqualTo("https://i.pinimg.com/600x/a.jpg");
    }

    @Test
    @DisplayName("태그가 틀린 핀도 오류를 담아 저장한다 — 핀 하나의 오타로 배치가 멈추지 않는다")
    void savesMalformedPinWithErrors() {
        when(client.listBoardPins(BOARD_ID, null)).thenReturn(new PinterestPageResponse<>(List.of(
                pin("30", "@otboo temp:5~8 sky:cloudy style:minimal gender:unisex"),
                pin("31", "태그 없는 핀"),
                pin("32", TAGGED)), null));

        SyncResult result = service.syncBoard(BOARD_ID, NOW);

        assertThat(result.upserted()).isEqualTo(3);
        assertThat(result.notSearchable()).isEqualTo(2);
        assertThat(savedPins()).extracting(PinterestPin::getTagStatus)
                .containsExactly(TagStatus.MALFORMED, TagStatus.UNTAGGED, TagStatus.TAGGED);
        assertThat(savedPins().getFirst().getTagErrors()).isEqualTo("'temp' 에 쓸 수 없는 값: 5~8");
    }

    @Test
    @DisplayName("주소가 컬럼 길이를 넘으면 자르지 않고 버린다 — 잘린 주소는 열리지 않는다")
    void dropsOverlongLinkInsteadOfTruncating() {
        String longLink = "https://shop.example.com/" + "a".repeat(2048);
        PinterestPinResponse item = new PinterestPinResponse("40", BOARD_ID, longLink, "제목", TAGGED, null, media());
        when(client.listBoardPins(BOARD_ID, null))
                .thenReturn(new PinterestPageResponse<>(List.of(item), null));

        service.syncBoard(BOARD_ID, NOW);

        assertThat(savedPins()).singleElement()
                .satisfies(pin -> {
                    assertThat(pin.getLink()).isNull();
                    assertThat(pin.getTitle()).isEqualTo("제목");
                });
    }

    @SuppressWarnings("unchecked")
    private List<PinterestPin> savedPins() {
        ArgumentCaptor<List<PinterestPin>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static PinterestPinResponse pin(String id, String description) {
        return new PinterestPinResponse(id, BOARD_ID, "https://shop.example.com/item", null,
                description, null, media());
    }

    private static PinterestPinResponse.Media media() {
        return new PinterestPinResponse.Media("image", new PinterestPinResponse.Images(
                null,
                new PinterestPinResponse.Image("https://i.pinimg.com/600x/a.jpg", 600, 900),
                null,
                null));
    }
}
