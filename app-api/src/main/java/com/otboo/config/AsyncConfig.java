package com.otboo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "eventTaskExecutor")
    public TaskExecutor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-async-");
        executor.setTaskDecorator(runnable -> {
            SecurityContext parentContext = SecurityContextHolder.getContext();

            return () -> {
                try {
                    SecurityContextHolder.setContext(parentContext);
                    runnable.run();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
        });
        executor.initialize();
        return executor;
    }

    /**
     * FASHN 요청은 한 건에 수 초~십수 초 걸린다(이미지 업로드). 알림용 eventTaskExecutor 와 섞이면
     * 알림이 밀리므로 따로 둔다. 가득 차서 못 받은 job 은 PENDING 으로 남아 스케줄러가 줍는다.
     */
    @Bean(name = "fittingDispatchExecutor")
    public TaskExecutor fittingDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("fitting-dispatch-");
        executor.setRejectedExecutionHandler((task, pool) ->
            log.warn("virtual_try_on_dispatch_rejected — 스케줄러가 이어서 처리한다"));
        executor.initialize();
        return executor;
    }

    /**
     * outbox Relay 를 커밋 직후 깨우는 전용 실행기. 한 번에 하나만 돌고 하나만 기다린다.
     */
    @Bean(name = "outboxRelayExecutor")
    public TaskExecutor outboxRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("outbox-relay-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return eventTaskExecutor();
    }
}
