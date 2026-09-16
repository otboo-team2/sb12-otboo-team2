-- V3 — 가상 피팅 job 캐시 재사용 추적 컬럼 (양정우)
--
-- reuse_base_cache_id
--   job이 어떤 캐시를 베이스로 시작했는지 기록한다.
--   캐시 재사용 로직(부분 매칭)은 상의/하의 중 하나만 같으면
--   이전 결과 이미지를 이어받아 나머지 단계만 새로 생성하는데,
--   이 시작점은 job이 성공하기 전부터 필요하다.
--   generation_depth 계산과 상태 추적에도 쓰인다.
--
-- model_hash
--   캐시를 "같은 사람 사진"끼리만 매칭하기 위한 식별 값이다.
--   원본 이미지를 SHA-256으로 해시해 저장해두고 비교 조건으로 쓴다.

ALTER TABLE virtual_try_on_jobs
    ADD COLUMN reuse_base_cache_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER result_cache_id,
    ADD CONSTRAINT fk_try_on_reuse_base FOREIGN KEY (reuse_base_cache_id)
        REFERENCES virtual_try_on_caches (id) ON DELETE SET NULL;
ALTER TABLE virtual_try_on_jobs
    ADD COLUMN model_hash VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER model_image_key;
