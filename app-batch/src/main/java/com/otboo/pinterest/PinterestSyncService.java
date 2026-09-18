package com.otboo.pinterest;

import com.otboo.pinterest.client.PinterestClient;
import com.otboo.pinterest.client.PinterestPageResponse;
import com.otboo.pinterest.client.PinterestPinResponse;
import com.otboo.pinterest.entity.PinterestPin;
import com.otboo.pinterest.repository.PinterestPinRepository;
import com.otboo.pinterest.tag.OutfitTagParseResult;
import com.otboo.pinterest.tag.OutfitTagParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 큐레이션 보드의 핀을 {@code pinterest_pins} 로 옮긴다.
 *
 * <h2>왜 배치인가</h2>
 * 추천은 Pinterest 를 실시간으로 부르지 않는다. 키워드로 Pinterest 전체를 검색하는 API 는 베타라
 * 열리지 않고, Trial 등급은 하루 호출 상한이 앱 단위다 — 사용자 요청마다 부르면 오전에 한도가 끝난다.
 * 그래서 이 배치가 핀을 우리 DB 로 옮기고, 추천은
 * {@link PinterestPinRepository#findTaggedFor} 로 그 테이블에서 찾는다.
 *
 * <h2>트랜잭션은 스텝이 쥔다</h2>
 * 여기에 {@code @Transactional} 을 붙이지 않는다. 이 서비스를 부르는
 * {@link PinterestSyncTasklet} 이 스텝 트랜잭션 안에서 돌아 그 트랜잭션이 저장까지 덮는다.
 * 그래서 보드 하나가 외부 호출에 실패해도 태스크릿이 삼키고 넘어가면 앞선 보드의 결과는 남는다.
 *
 * <h2>핀 하나의 문제로 멈추지 않는다</h2>
 * 태그 오타는 예외가 아니라 {@link OutfitTagParseResult} 의 상태로 저장한다. 큐레이터가 고칠 내용이
 * {@code tag_errors} 에 남고, 그 핀만 추천에서 빠진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PinterestSyncService {

    /**
     * 한 보드에서 넘길 최대 페이지 수.
     *
     * <p>bookmark 가 끝나지 않으면 루프가 멈추지 않는다. 상대 API 가 그럴 리 없다고 가정하고
     * 무한 루프를 열어두면, 그 사고가 곧 하루 호출 한도 소진이다. 기본 page_size 100 이면
     * 보드당 2,000 핀까지 — 큐레이션 보드에는 충분하다.
     */
    static final int MAX_PAGES_PER_BOARD = 20;

    /** 컬럼 길이. 넘치면 저장할 때 DB 가 거절한다 — 외부에서 온 값이라 여기서 맞춘다. */
    private static final int MAX_DESCRIPTION_LENGTH = 800;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_URL_LENGTH = 2048;

    private final PinterestClient client;
    private final PinterestPinRepository repository;

    /**
     * 보드 하나를 끝까지(또는 {@link #MAX_PAGES_PER_BOARD} 까지) 동기화한다.
     *
     * @param syncedAt 이번 실행의 기준 시각. 보드마다 다시 찍지 않는다 — 한 번의 실행으로 들어온
     *                 핀들이 같은 값을 가져야 "이번에 안 들어온 핀"을 나중에 골라낼 수 있다
     */
    public SyncResult syncBoard(String boardId, Instant syncedAt) {
        SyncResult result = SyncResult.EMPTY;
        String bookmark = null;
        for (int page = 1; page <= MAX_PAGES_PER_BOARD; page++) {
            PinterestPageResponse<PinterestPinResponse> response = client.listBoardPins(boardId, bookmark);
            result = result.plus(upsertPage(boardId, response.items(), syncedAt));
            if (!response.hasNext()) {
                return result;
            }
            bookmark = response.bookmark();
        }
        log.warn("Pinterest board sync stopped at page limit. board_id={}, max_pages={}, fetched={}",
                boardId, MAX_PAGES_PER_BOARD, result.fetched());
        return result;
    }

    private SyncResult upsertPage(String boardId, List<PinterestPinResponse> items, Instant syncedAt) {
        List<PinterestPinResponse> usable = new ArrayList<>(items.size());
        for (PinterestPinResponse item : items) {
            if (isUsable(item)) {
                usable.add(item);
            } else {
                log.debug("Skipped pin. board_id={}, pin_id={}", boardId, item.id());
            }
        }
        int skipped = items.size() - usable.size();
        if (usable.isEmpty()) {
            return new SyncResult(items.size(), 0, skipped, 0);
        }

        Map<String, PinterestPin> existing = repository
                .findAllByPinIdIn(usable.stream().map(PinterestPinResponse::id).toList())
                .stream()
                .collect(Collectors.toMap(PinterestPin::getPinId, Function.identity()));

        List<PinterestPin> pins = new ArrayList<>(usable.size());
        int notSearchable = 0;
        for (PinterestPinResponse item : usable) {
            // 파싱은 원문 전체로 한다. 저장할 때 자르는 것과 순서가 바뀌면 @otboo 줄이 잘려 나갈 수 있다.
            OutfitTagParseResult parsed = OutfitTagParser.parse(item.description());
            if (!parsed.isTagged()) {
                notSearchable++;
                log.info("Pin is not searchable. board_id={}, pin_id={}, tag_status={}, errors={}",
                        boardId, item.id(), parsed.status(), parsed.errors());
            }

            String imageUrl = item.imageUrl().orElseThrow();  // isUsable 에서 확인했다
            String link = withinLength(item.link(), MAX_URL_LENGTH);
            String title = truncate(item.title(), MAX_TITLE_LENGTH);
            String description = truncate(item.description(), MAX_DESCRIPTION_LENGTH);

            // 핀이 실제로 어느 보드에 있는지가 아니라 "우리가 무엇을 동기화했는지"를 남긴다.
            // 같은 핀을 여러 보드에 담아두면 pin_id 가 유니크라 마지막에 동기화한 보드가 남는다.
            PinterestPin pin = existing.get(item.id());
            if (pin == null) {
                pin = PinterestPin.create(item.id(), boardId, imageUrl, link, title, description, parsed, syncedAt);
            } else {
                pin.refresh(boardId, imageUrl, link, title, description, parsed, syncedAt);
            }
            pins.add(pin);
        }
        repository.saveAll(pins);
        return new SyncResult(items.size(), pins.size(), skipped, notSearchable);
    }

    /**
     * 저장할 수 있는 핀인지.
     *
     * <p>영상·여러 장짜리 핀은 {@code images} 가 없어 이미지 주소가 안 나온다. 코디 추천은 사진
     * 한 장으로 하니 건너뛴다. 이미지 주소는 {@code NOT NULL} 이라 없으면 저장 자체가 안 된다.
     */
    private static boolean isUsable(PinterestPinResponse item) {
        if (item.id() == null || item.id().isBlank()) {
            return false;
        }
        return item.imageUrl()
                .filter(url -> url.length() <= MAX_URL_LENGTH)
                .isPresent();
    }

    /** 주소는 자르면 못 쓰는 값이 된다. 길면 버린다 — {@code link} 는 {@code NULL} 을 허용한다. */
    private static String withinLength(String url, int maxLength) {
        if (url == null || url.isBlank() || url.length() > maxLength) {
            return null;
        }
        return url;
    }

    /**
     * Pinterest 쪽 상한(제목 100 · 설명 800)과 컬럼 길이가 같아서 평소에는 아무것도 자르지 않는다.
     * 상대가 상한을 올렸을 때 배치 전체가 저장 실패로 멈추지 않게 두는 방어선이다.
     */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }

    /**
     * 동기화 집계.
     *
     * @param fetched       Pinterest 가 준 핀 수
     * @param upserted      저장·갱신한 핀 수
     * @param skipped       이미지가 없어 건너뛴 핀 수
     * @param notSearchable 저장은 했지만 태그가 없거나 틀려 추천에서 빠지는 핀 수
     */
    public record SyncResult(int fetched, int upserted, int skipped, int notSearchable) {

        public static final SyncResult EMPTY = new SyncResult(0, 0, 0, 0);

        public SyncResult plus(SyncResult other) {
            return new SyncResult(
                    fetched + other.fetched,
                    upserted + other.upserted,
                    skipped + other.skipped,
                    notSearchable + other.notSearchable);
        }
    }
}
