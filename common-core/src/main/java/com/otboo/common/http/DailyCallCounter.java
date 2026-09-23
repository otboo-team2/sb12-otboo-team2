package com.otboo.common.http;

import java.time.Clock;
import java.time.LocalDate;

/**
 * API 한 곳의 하루 호출 수를 센다. LLM·이미지 생성처럼 과금되는 API 의 크레딧 방어용이다.
 *
 * <p>날짜 기준은 UTC 다. 프로젝트 전체가 UTC 로 통일돼 있으므로 여기만 KST 를 쓰면
 * 로그의 시각과 상한이 끊기는 시점이 어긋난다.
 *
 * <p><b>한계</b> — 인스턴스 메모리에만 있다. 앱을 여러 개 띄우면 인스턴스마다 따로 세므로
 * 실제 상한은 (상한 × 인스턴스 수)가 된다. 지금은 단일 인스턴스라 충분하고,
 * 스케일 아웃하면 Redis 카운터로 바꾸면 된다.
 */
final class DailyCallCounter {

    private final Clock clock;
    private LocalDate day;
    private long count;

    DailyCallCounter(Clock clock) {
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    /**
     * 호출 １회를 기록한다.
     *
     * @return 상한을 넘지 않아 호출해도 되면 {@code true}
     */
    synchronized boolean tryAcquire(Long limit) {
        if (limit == null) {
            return true;
        }
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            count = 0;
        }
        if (count >= limit) {
            return false;
        }
        count++;
        return true;
    }

    synchronized long currentCount() {
        return LocalDate.now(clock).equals(day) ? count : 0;
    }
}
