package com.otboo.recommendation.search.elasticsearch;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.clothes.search.ClothesIndexEvent;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

class RecommendationClothesTransactionEventTest {
    @Test
    void commitDispatchesOnlyAfterCommit() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var indexer = context.getBean(RecommendationClothesIndexer.class);
            var connection = context.getBean(Connection.class);
            UUID id = UUID.randomUUID();
            new TransactionTemplate(context.getBean(DataSourceTransactionManager.class)).executeWithoutResult(s -> {
                context.publishEvent(ClothesIndexEvent.upsert(id));
                verifyNoInteractions(indexer);
            });
            drain(context);
            verify(connection).commit();
            verify(indexer).index(id);
        }
    }

    @Test
    void rollbackDoesNotDispatch() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var indexer = context.getBean(RecommendationClothesIndexer.class);
            var connection = context.getBean(Connection.class);
            new TransactionTemplate(context.getBean(DataSourceTransactionManager.class)).executeWithoutResult(s -> {
                context.publishEvent(ClothesIndexEvent.delete(UUID.randomUUID()));
                s.setRollbackOnly();
            });
            drain(context);
            verify(connection).rollback();
            verifyNoInteractions(indexer);
        }
    }

    private void drain(AnnotationConfigApplicationContext context) throws Exception {
        assertThat(context.getBean(ThreadPoolTaskExecutor.class).submit(() -> true).get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    static class Config {
        @Bean Connection connection() throws Exception {
            var c = mock(Connection.class);
            when(c.getAutoCommit()).thenReturn(true);
            return c;
        }
        @Bean DataSourceTransactionManager transactionManager(Connection connection) throws Exception {
            var ds = mock(DataSource.class);
            when(ds.getConnection()).thenReturn(connection);
            return new DataSourceTransactionManager(ds);
        }
        @Bean RecommendationClothesIndexer indexer() { return mock(RecommendationClothesIndexer.class); }
        @Bean RecommendationClothesIndexEventListener listener(RecommendationClothesIndexer indexer) {
            return new RecommendationClothesIndexEventListener(indexer);
        }
        @Bean ThreadPoolTaskExecutor taskExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setWaitForTasksToCompleteOnShutdown(true);
            return executor;
        }
    }
}
