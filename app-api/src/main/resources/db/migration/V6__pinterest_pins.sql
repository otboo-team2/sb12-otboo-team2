-- V6 — Pinterest 코디 핀 인덱스
--
-- 코디 추천은 Pinterest 실시간 검색이 아님.
--   1) 전체 검색 API(search/partner/pins)는 베타라 일반 앱에 열리지 않는다
--   2) Trial 등급은 앱 단위 하루 호출 1000 limit있음.
-- 그래서 배치가 팀이 큐레이션한 보드의 핀을 이 테이블로 옮겨 오고, 추천은 여기서 검색한다.
--
-- 태그는 핀 description 의 "@otboo temp:5-8 sky:cloudy style:minimal ..." 줄에서 읽는다.
-- 이미지는 복사하지 않고 Pinterest 주소만 저장한다.

CREATE TABLE pinterest_pins
(
    id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    -- PK 와 달리 ascii 로 두지 않는다. 외부에서 온 문자열로 조회하는 자리라, ascii 컬럼에
    -- 한글 같은 값이 비교로 들어오면 "Illegal mix of collations" 로 쿼리 자체가 실패한다.
    -- Pinterest ID 는 숫자뿐이고 행도 수백~수천 개라 utf8mb4 로 둬도 인덱스 부담이 거의 없다.
    pin_id      VARCHAR(64)                                      NOT NULL COMMENT 'Pinterest 핀 ID',
    board_id    VARCHAR(64)                                      NOT NULL COMMENT 'Pinterest 보드 ID',
    image_url   VARCHAR(2048)                                    NOT NULL COMMENT 'Pinterest 이미지 주소. 복사하지 않는다',
    link        VARCHAR(2048)                                    NULL COMMENT '핀 출처 링크. 상품 페이지가 아닐 수 있다',
    title       VARCHAR(100)                                     NULL,
    -- 원문을 남긴다. 태그 어휘가 바뀌면 Pinterest 를 다시 부르지 않고 이 값만 다시 파싱한다.
    description VARCHAR(800)                                     NULL,
    tag_status  VARCHAR(16)                                      NOT NULL COMMENT '추천 검색 대상은 TAGGED 뿐',
    tag_errors  VARCHAR(1000)                                    NULL COMMENT '큐레이터가 고칠 내용',
    -- MALFORMED 면 읽어낸 만큼만 들어 있어 NULL 일 수 있다
    temp_band   VARCHAR(16)                                      NULL,
    sky         VARCHAR(16)                                      NULL,
    gender      VARCHAR(16)                                      NULL,
    synced_at   DATETIME(6)                                      NOT NULL COMMENT '마지막으로 동기화한 시각',
    created_at  DATETIME(6)                                      NOT NULL,
    updated_at  DATETIME(6)                                      NOT NULL,
    PRIMARY KEY (id),
    -- 배치가 같은 핀을 매번 다시 받는다. upsert 기준이자 중복 방지.
    UNIQUE KEY uk_pinterest_pins_pin_id (pin_id),
    KEY idx_pinterest_pins_lookup (tag_status, temp_band, sky, gender),
    CONSTRAINT ck_pinterest_pins_tag_status CHECK (tag_status IN ('TAGGED', 'MALFORMED', 'UNTAGGED')),
    CONSTRAINT ck_pinterest_pins_temp_band
        CHECK (temp_band IN ('T28UP', 'T23_27', 'T20_22', 'T17_19', 'T12_16', 'T9_11', 'T5_8', 'T4DOWN')),
    CONSTRAINT ck_pinterest_pins_sky CHECK (sky IN ('CLEAR', 'CLOUDY', 'RAIN', 'SNOW')),
    CONSTRAINT ck_pinterest_pins_gender CHECK (gender IN ('MEN', 'WOMEN', 'UNISEX'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT 'Pinterest 코디 핀 인덱스';


-- style · item 은 값이 여러 개라 자식 테이블로 둔다. MySQL 에는 배열 컬럼이 없다.
CREATE TABLE pinterest_pin_tags
(
    id               CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pinterest_pin_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tag_type         VARCHAR(16)                                   NOT NULL,
    tag_value        VARCHAR(32)                                   NOT NULL COMMENT 'enum 이름. 예: MINIMAL',
    created_at       DATETIME(6)                                   NOT NULL,
    updated_at       DATETIME(6)                                   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pinterest_pin_tags (pinterest_pin_id, tag_type, tag_value),
    -- "style 이 MINIMAL 인 핀" 을 태그 쪽에서 먼저 좁혀 들어가는 조회용
    KEY idx_pinterest_pin_tags_value (tag_type, tag_value, pinterest_pin_id),
    CONSTRAINT fk_pinterest_pin_tags_pin FOREIGN KEY (pinterest_pin_id)
        REFERENCES pinterest_pins (id) ON DELETE CASCADE,
    CONSTRAINT ck_pinterest_pin_tags_type CHECK (tag_type IN ('STYLE', 'ITEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '핀의 style · item 태그';
