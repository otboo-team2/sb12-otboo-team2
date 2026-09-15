package com.otboo.feed.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 검색 설정. {@code otboo.search.*}
 *
 * @param engine            검색 엔진. 기본: MySQL, ES 없이 App Active
 * @param indexName         피드 인덱스의 nickname. 실제 인덱스는 {@code <alias>-vN} 이다
 * @param fallbackToMysql   ES 호출이 실패했을 때 MySQL 검색으로 내려갈지
 * @param indexOnStartup    기동 시 인덱스가 비어 있으면 전체 색인을 채울지
 * @param bulkSize          전체 재색인 시 한 번에 보내는 문서 수
 */
@ConfigurationProperties(prefix = "otboo.search")
public record SearchProperties(
        SearchEngine engine,
        String indexName,
        boolean fallbackToMysql,
        boolean indexOnStartup,
        int bulkSize
) {

    public SearchProperties {
        if (engine == null) {
            engine = SearchEngine.MYSQL;
        }
        if (indexName == null || indexName.isBlank()) {
            indexName = "feeds";
        }
        if (bulkSize <= 0) {
            bulkSize = 500;
        }
    }

    public boolean usesElasticsearch() {
        return engine == SearchEngine.ELASTICSEARCH;
    }
}
