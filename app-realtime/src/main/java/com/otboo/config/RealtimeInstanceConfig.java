package com.otboo.config;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이 인스턴스만의 컨슈머 그룹 id. 인스턴스가 여러 대일 때 각자 고유한 그룹이어야
 * Kafka가 메시지를 인스턴스 전원에게 뿌려준다(공유 그룹이면 파티션을 나눠 가져서
 * 절반만 받는 인스턴스가 생김 — 유저 연결이 어느 인스턴스에 있는지 몰라서
 * 전 인스턴스가 다 받아야 함).
 */
@Configuration
public class RealtimeInstanceConfig {

    @Bean
    public String realtimeConsumerGroupId() {
        return "app-realtime-" + UUID.randomUUID();
    }
}
