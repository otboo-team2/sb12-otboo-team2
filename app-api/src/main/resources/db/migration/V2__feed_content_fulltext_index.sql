-- V2 — 피드 본문 전문검색 인덱스 (류승지)
--
-- 왜 필요한가
--   피드 검색(GET /api/feeds?keywordLike=)을 LIKE '%키워드%' 로 구현하면 앞에 와일드카드가 붙어
--   인덱스를 탈 수 없다. 즉 검색 한 번에 feeds 전체를 읽는다. 피드가 쌓일수록 선형으로 느려진다.
--
-- 왜 ngram 파서인가
--   MySQL 의 기본 FULLTEXT 파서는 공백으로 단어를 나눈다. 한국어는 그렇게 갈리지 않아
--   "코트" 로 검색하면 "겨울코트를 꺼냈다" 가 걸리지 않는다.
--   ngram 파서는 본문을 2글자 단위로 쪼개 색인하므로 부분 일치가 된다.
--
--   ⚠️ 토큰 크기는 서버 변수 ngram_token_size 를 따르며 기본값이 2 다. 즉 1글자 검색어는
--      색인에 존재하지 않아 MATCH 로 찾을 수 없다. 애플리케이션이 그 경우만 LIKE 로 내려간다
--      (SearchKeyword.isFullTextSearchable).
--
-- 비용
--   INSERT/UPDATE 때 색인 갱신 비용이 붙는다. 피드는 읽기가 압도적으로 많아 감수할 만하다.
--   부하가 문제가 되면 otboo.search.engine=opensearch 로 검색을 분리한다.

ALTER TABLE feeds
    ADD FULLTEXT INDEX ft_feeds_content (content) WITH PARSER ngram;
