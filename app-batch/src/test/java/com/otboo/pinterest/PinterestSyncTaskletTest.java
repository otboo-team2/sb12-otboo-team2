package com.otboo.pinterest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.pinterest.PinterestSyncService.SyncResult;
import com.otboo.pinterest.exception.PinterestErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PinterestSyncTaskletTest {

    private PinterestSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = mock(PinterestSyncService.class);
        when(syncService.syncBoard(any(), any())).thenReturn(SyncResult.EMPTY);
    }

    @Test
    @DisplayName("보드 설정이 없으면 Pinterest 를 부르지 않는다")
    void doesNothingWithoutBoardIds() {
        tasklet(List.of()).execute(null, null);

        verifyNoInteractions(syncService);
    }

    @Test
    @DisplayName("보드 하나가 실패해도 다음 보드로 넘어간다")
    void continuesAfterBoardFailure() {
        when(syncService.syncBoard(eq("1"), any()))
                .thenThrow(new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR));

        tasklet(List.of("1", "2")).execute(null, null);

        verify(syncService).syncBoard(eq("2"), any());
    }

    @Test
    @DisplayName("보드 ID 오타는 그 보드만 건너뛴다")
    void continuesAfterInvalidBoardId() {
        when(syncService.syncBoard(eq("oops"), any()))
                .thenThrow(new BusinessException(PinterestErrorCode.INVALID_BOARD_ID));

        tasklet(List.of("oops", "2")).execute(null, null);

        verify(syncService).syncBoard(eq("2"), any());
    }

    @Test
    @DisplayName("토큰이 없으면 잡을 실패시킨다 — 초록불로 끝나고 테이블만 비어 있으면 안 된다")
    void failsFastWhenTokenIsMissing() {
        when(syncService.syncBoard(eq("1"), any()))
                .thenThrow(new BusinessException(PinterestErrorCode.ACCESS_TOKEN_NOT_CONFIGURED));

        assertThatThrownBy(() -> tasklet(List.of("1", "2")).execute(null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PinterestErrorCode.ACCESS_TOKEN_NOT_CONFIGURED);

        verify(syncService, never()).syncBoard(eq("2"), any());
    }

    @Test
    @DisplayName("하루 호출 상한에 걸리면 남은 보드를 시도하지 않는다 — 결과가 같다")
    void stopsWhenDailyLimitReached() {
        when(syncService.syncBoard(eq("1"), any()))
                .thenThrow(new BusinessException(CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED));

        tasklet(List.of("1", "2")).execute(null, null);

        verify(syncService, never()).syncBoard(eq("2"), any());
    }

    private PinterestSyncTasklet tasklet(List<String> boardIds) {
        PinterestProperties properties = new PinterestProperties(null, "token", boardIds, null);
        return new PinterestSyncTasklet(properties, syncService, new SimpleMeterRegistry());
    }
}
