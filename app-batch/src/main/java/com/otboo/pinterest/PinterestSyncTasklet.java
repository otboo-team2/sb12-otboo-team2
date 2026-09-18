package com.otboo.pinterest;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.pinterest.PinterestSyncService.SyncResult;
import com.otboo.pinterest.exception.PinterestErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * 설정한 보드들을 돌며 핀을 동기화한다.
 *
 * <h2>어디까지 참고 어디서 멈추는가</h2>
 * 보드 하나의 문제(그 보드만 없거나 그 호출만 실패)는 삼키고 다음 보드로 간다. 반면
 * 토큰 미설정처럼 보드를 바꿔도 같을 문제는 그대로 던진다 — 잡이 초록불로 끝나고 테이블만
 * 비어 있는 상황을 만들지 않는다.
 *
 * <p>하루 호출 상한에 걸리면 남은 보드도 결과가 같으므로 거기서 끝낸다. 경고를 보드 수만큼
 * 쌓아봐야 원인은 하나다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PinterestSyncTasklet implements Tasklet {

    private final PinterestProperties properties;
    private final PinterestSyncService syncService;
    private final MeterRegistry meterRegistry;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<String> boardIds = properties.boardIds();
        if (boardIds.isEmpty()) {
            // 토큰만 넣고 보드를 안 넣으면 조용히 0건으로 끝난다. 설정 누락과 "핀이 없음"을 구분해 남긴다.
            log.warn("Pinterest sync skipped. otboo.pinterest.board-ids is empty");
            return RepeatStatus.FINISHED;
        }

        Timer.Sample timer = Timer.start(meterRegistry);
        Instant syncedAt = Instant.now();
        SyncResult total = SyncResult.EMPTY;
        int failedBoards = 0;

        log.info("Starting Pinterest sync. board_count={}", boardIds.size());
        for (String boardId : boardIds) {
            try {
                total = total.plus(syncService.syncBoard(boardId, syncedAt));
            } catch (BusinessException e) {
                if (e.getErrorCode() == CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED) {
                    failedBoards++;
                    log.warn("Pinterest daily call limit reached. Stopping. board_id={}, remaining_boards={}",
                            boardId, boardIds.size() - boardIds.indexOf(boardId) - 1);
                    break;
                }
                if (!isBoardFailure(e)) {
                    throw e;
                }
                failedBoards++;
                log.warn("Pinterest board sync failed. Continuing. board_id={}, error_code={}",
                        boardId, e.getErrorCode().getCode());
            }
        }

        meterRegistry.counter("pinterest.sync.upserted").increment(total.upserted());
        meterRegistry.counter("pinterest.sync.skipped").increment(total.skipped());
        meterRegistry.counter("pinterest.sync.not_searchable").increment(total.notSearchable());
        meterRegistry.counter("pinterest.sync.board.failure").increment(failedBoards);
        timer.stop(meterRegistry.timer("pinterest.sync.duration"));
        log.info("Finished Pinterest sync. fetched={}, upserted={}, skipped={}, not_searchable={}, failed_boards={}",
                total.fetched(), total.upserted(), total.skipped(), total.notSearchable(), failedBoards);
        return RepeatStatus.FINISHED;
    }

    /** 이 보드만의 문제인가(다음 보드로 간다), 앱 전체의 문제인가(멈춘다). */
    private boolean isBoardFailure(BusinessException exception) {
        var code = exception.getErrorCode();
        return code == CommonErrorCode.EXTERNAL_API_ERROR
                || code == CommonErrorCode.EXTERNAL_API_TIMEOUT
                // 보드 ID 하나가 오타여도 나머지 보드는 정상이다
                || code == PinterestErrorCode.INVALID_BOARD_ID;
    }
}
