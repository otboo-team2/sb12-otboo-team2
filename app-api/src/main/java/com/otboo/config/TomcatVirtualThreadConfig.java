package com.otboo.config;

import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTP 요청 처리 스레드만 가상 스레드로 바꾼다. 기본은 꺼짐(플랫폼 스레드 200).
 *
 * <p>{@code spring.threads.virtual.enabled} 를 쓰지 않는 이유 — 그 설정은 톰캣 외에 {@code @Scheduled}
 * 스케줄러(가상 피팅 폴러)·Kafka 리스너까지 함께 바꾼다. 여기서 보려는 것은 "요청 스레드가 외부 대기에
 * 묶일 때"의 효과뿐이라 범위를 톰캣으로 좁힌다. 이벤트·가상 피팅용 실행기({@link AsyncConfig})는 그대로다.
 *
 * <p>켜면 {@code server.tomcat.threads.max} 가 더 이상 동시 처리 상한이 아니고, {@code tomcat_threads_busy_threads}
 * 지표도 의미가 없어진다. 동시 처리 수는 {@code http_server_requests_active_seconds_active_count} 로 본다.
 */
@Configuration
@ConditionalOnProperty(name = "otboo.web.virtual-threads.enabled", havingValue = "true")
public class TomcatVirtualThreadConfig {

    @Bean
    public TomcatProtocolHandlerCustomizer<ProtocolHandler> virtualThreadProtocolHandlerCustomizer() {
        // 스프링 부트가 spring.threads.virtual.enabled 일 때 쓰는 것과 같은 실행기다.
        return protocolHandler -> protocolHandler.setExecutor(new VirtualThreadExecutor("tomcat-handler-"));
    }
}
