package com.otboo.feed.search;

/**
 * 피드 검색에 쓸 엔진. {@code otboo.search.engine} 으로 고른다.
 * Elasticsearch 가 없는 환경(팀원 로컬, CI 의 단위 테스트)에서의 환경.
 */
public enum SearchEngine {

    MYSQL,
    ELASTICSEARCH
}
