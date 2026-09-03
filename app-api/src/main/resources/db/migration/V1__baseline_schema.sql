-- V1 baseline — 전 파트 취합본
--
-- 공통 규칙
--   · 시각은 UTC 기준 DATETIME(6)
--   · PK/FK 는 CHAR(36) CHARACTER SET ascii (utf8mb4 로 두면 36자가 144바이트가 된다)
--   · 논리삭제 없음
--   · 커서 페이지네이션용 인덱스는 (정렬키, id) 순

-- ── 사용자 · 인증 (여운정) ───────────────────────────────────────────────────

CREATE TABLE users
(
    id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email      VARCHAR(320)                                   NOT NULL,
    password   VARCHAR(100)                                   NULL, -- 소셜 전용 계정은 비밀번호가 없다
    name       VARCHAR(50)                                    NOT NULL,
    role       VARCHAR(16)                                    NOT NULL DEFAULT 'USER',
    locked     BOOLEAN                                        NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)                                    NOT NULL,
    updated_at DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_created_at (created_at, id),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '계정';


-- ── 날씨 (박교현) ────────────────────────────────────────────────────────────

CREATE TABLE weather_regions
(
    id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    grid_x         INT                                           NOT NULL,
    grid_y         INT                                           NOT NULL,
    location_names JSON                                          NOT NULL, -- ["서울특별시","강남구",...]
    created_at     DATETIME(6)                                   NOT NULL,
    updated_at     DATETIME(6)                                   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_weather_regions_grid (grid_x, grid_y)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '격자 ↔ 행정구역명';


CREATE TABLE weathers
(
    id                                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    grid_x                             INT                                           NOT NULL,
    grid_y                             INT                                           NOT NULL,
    forecasted_at                      DATETIME(6)                                   NOT NULL COMMENT '예보 발표 시각',
    forecast_at                        DATETIME(6)                                   NOT NULL COMMENT '예보 대상 시각',
    sky_status                         VARCHAR(32)                                   NOT NULL,
    precipitation_type                 VARCHAR(32)                                   NOT NULL,
    precipitation_amount               DECIMAL(10, 2)                                NOT NULL,
    precipitation_probability          DECIMAL(5, 2)                                 NOT NULL,
    humidity_current                   DECIMAL(5, 2)                                 NOT NULL,
    temperature_current                DECIMAL(5, 2)                                 NOT NULL,
    temperature_min                    DECIMAL(5, 2)                                 NOT NULL,
    temperature_max                    DECIMAL(5, 2)                                 NOT NULL,
    wind_speed                         DECIMAL(6, 2)                                 NOT NULL,
    wind_speed_as_word                 VARCHAR(16)                                   NOT NULL,
    -- 수집 첫날엔 전날 예보가 없어 계산할 수 없다
    humidity_compared_to_day_before    DECIMAL(5, 2)                                 NULL,
    temperature_compared_to_day_before DECIMAL(5, 2)                                 NULL,
    created_at                         DATETIME(6)                                   NOT NULL,
    updated_at                         DATETIME(6)                                   NOT NULL,
    PRIMARY KEY (id),
    -- 배치가 같은 예보를 다시 받는다. upsert 기준이자 중복 방지.
    UNIQUE KEY uk_weathers_grid_forecast (grid_x, grid_y, forecasted_at, forecast_at),
    KEY idx_weathers_grid_forecast_at (grid_x, grid_y, forecast_at),
    CONSTRAINT ck_weathers_sky_status CHECK (sky_status IN ('CLEAR', 'MOSTLY_CLOUDY', 'CLOUDY')),
    CONSTRAINT ck_weathers_precipitation_type
        CHECK (precipitation_type IN ('NONE', 'RAIN', 'RAIN_SNOW', 'SNOW', 'SHOWER')),
    CONSTRAINT ck_weathers_wind_speed_as_word
        CHECK (wind_speed_as_word IN ('WEAK', 'MODERATE', 'STRONG'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '수집한 날씨 예보';


-- ── 프로필 (여운정) ──────────────────────────────────────────────────────────

CREATE TABLE profiles
(
    id                      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    gender                  VARCHAR(16)                                    NULL,
    birth_date              DATE                                           NULL,
    latitude                DECIMAL(10, 6)                                 NULL,
    longitude               DECIMAL(10, 6)                                 NULL,
    -- 격자 좌표와 지명은 weather_regions 를 조인해서 채운다
    region_id               CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    location_updated_at     DATETIME(6)                                    NULL,
    temperature_sensitivity TINYINT                                        NULL,
    profile_image_url       VARCHAR(500)                                   NULL,
    created_at              DATETIME(6)                                    NOT NULL,
    updated_at              DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profiles_user_id (user_id),
    KEY idx_profiles_region (region_id),
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_profiles_region FOREIGN KEY (region_id)
        REFERENCES weather_regions (id) ON DELETE SET NULL,
    CONSTRAINT ck_profiles_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT ck_profiles_temperature_sensitivity CHECK (temperature_sensitivity BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '프로필';
-- 이름은 users.name 에만 둔다. ProfileDto.name 은 조인해서 채운다.


CREATE TABLE user_oauth_accounts
(
    id               CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider         VARCHAR(16)                                    NOT NULL,
    provider_user_id VARCHAR(191)                                   NOT NULL,
    created_at       DATETIME(6)                                    NOT NULL,
    updated_at       DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    -- 같은 소셜 계정이 두 명에게 붙는 것을 막는다
    UNIQUE KEY uk_oauth_provider_user (provider, provider_user_id),
    KEY idx_oauth_user (user_id),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_oauth_provider CHECK (provider IN ('GOOGLE', 'KAKAO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '소셜 로그인 연결';


CREATE TABLE refresh_tokens
(
    id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL, -- 원문이 아니라 SHA-256 해시
    expires_at DATETIME(6)                                    NOT NULL,
    created_at DATETIME(6)                                    NOT NULL,
    updated_at DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user (user_id),
    KEY idx_refresh_expires_at (expires_at), -- 만료 토큰 정리 배치용
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '리프레시 토큰';


-- ── 의상 (안소현) ────────────────────────────────────────────────────────────

CREATE TABLE clothes
(
    id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name       VARCHAR(500)                                   NOT NULL,
    type       VARCHAR(32)                                    NOT NULL,
    image_url  VARCHAR(500)                                   NULL,
    created_at DATETIME(6)                                    NOT NULL,
    updated_at DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_clothes_owner_type (owner_id, type, id),
    KEY idx_clothes_owner (owner_id, id),
    -- 사용자가 지워졌다고 의상이 자동으로 사라지면 안 된다
    CONSTRAINT fk_clothes_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_clothes_type CHECK (type IN
                                      ('TOP', 'BOTTOM', 'DRESS', 'OUTER', 'UNDERWEAR', 'ACCESSORY',
                                       'SHOES', 'SOCKS', 'HAT', 'BAG', 'SCARF', 'ETC'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '의상';


CREATE TABLE clothes_attribute_definitions
(
    id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name       VARCHAR(100)                                   NOT NULL,
    created_at DATETIME(6)                                    NOT NULL,
    updated_at DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attribute_definition_name (name),
    KEY idx_attribute_definition_created_at (created_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '동적 의상 속성 정의';


CREATE TABLE clothes_attribute_selectable_values
(
    id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    definition_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    value         VARCHAR(100)                                   NOT NULL,
    created_at    DATETIME(6)                                    NOT NULL,
    updated_at    DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_selectable_definition_value (definition_id, value),
    -- 아래 복합 FK 가 참조하는 대상. 없으면 복합 FK 를 만들 수 없다.
    UNIQUE KEY uk_selectable_definition_id (definition_id, id),
    CONSTRAINT fk_selectable_definition FOREIGN KEY (definition_id)
        REFERENCES clothes_attribute_definitions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '속성별 선택 가능한 값';


CREATE TABLE clothes_attribute_values
(
    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    clothes_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    definition_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    selectable_value_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at          DATETIME(6)                                    NOT NULL,
    updated_at          DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_clothes_attribute_definition (clothes_id, definition_id),
    KEY idx_clothes_attribute_selectable (definition_id, selectable_value_id),
    CONSTRAINT fk_clothes_attribute_clothes FOREIGN KEY (clothes_id)
        REFERENCES clothes (id) ON DELETE CASCADE,
    -- 속성과 선택값의 짝이 맞는지 DB 가 검사한다. (색상, WINTER) 같은 연결을 막는다.
    CONSTRAINT fk_clothes_attribute_selectable FOREIGN KEY (definition_id, selectable_value_id)
        REFERENCES clothes_attribute_selectable_values (definition_id, id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '의상에 적용된 속성값';


-- ── AI 추천 (류승지 + 박교현) ────────────────────────────────────────────────

CREATE TABLE recommendation_histories
(
    id                CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    weather_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    -- LLM 캐시 히트 키. 예: temp:10-15|humid:40-60|rain:0-30
    weather_signature VARCHAR(100)                                   NOT NULL,
    prompt_summary    VARCHAR(1000)                                  NULL,
    created_at        DATETIME(6)                                    NOT NULL,
    updated_at        DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_recommendation_user (user_id, created_at, id),
    KEY idx_recommendation_signature (weather_signature),
    CONSTRAINT fk_recommendation_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- 오래된 예보가 정리돼도 추천 이력은 남긴다
    CONSTRAINT fk_recommendation_weather FOREIGN KEY (weather_id)
        REFERENCES weathers (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT 'AI 추천 이력';


CREATE TABLE recommendation_items
(
    id                CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recommendation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    category          VARCHAR(30)                                    NOT NULL,
    llm_keyword       VARCHAR(255)                                   NOT NULL,
    image_source      VARCHAR(500)                                   NOT NULL,
    order_index       INT                                            NOT NULL,
    created_at        DATETIME(6)                                    NOT NULL,
    updated_at        DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recommendation_item_order (recommendation_id, order_index),
    CONSTRAINT fk_recommendation_item_history FOREIGN KEY (recommendation_id)
        REFERENCES recommendation_histories (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT 'AI 추천 항목';


-- ── 피드 · 팔로우 (류승지) ───────────────────────────────────────────────────

CREATE TABLE feeds
(
    id                CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    author_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    weather_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recommendation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, -- AI 추천 기반으로 올린 경우만
    content           TEXT                                           NOT NULL,
    -- NULL 을 허용하면 sortBy=likeCount 정렬에서 커서 순서가 깨진다
    like_count        BIGINT                                         NOT NULL DEFAULT 0,
    comment_count     INT                                            NOT NULL DEFAULT 0,
    created_at        DATETIME(6)                                    NOT NULL,
    updated_at        DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_feeds_created_at (created_at, id),
    KEY idx_feeds_like_count (like_count, id),
    KEY idx_feeds_author (author_id, created_at, id),
    KEY idx_feeds_weather (weather_id),
    KEY idx_feeds_recommendation (recommendation_id),
    CONSTRAINT fk_feeds_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE CASCADE,
    -- 피드에 박힌 그날 날씨는 지울 수 없다
    CONSTRAINT fk_feeds_weather FOREIGN KEY (weather_id) REFERENCES weathers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_feeds_recommendation FOREIGN KEY (recommendation_id)
        REFERENCES recommendation_histories (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '피드(OOTD)';


CREATE TABLE feed_clothes
(
    id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    feed_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    clothes_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_index INT                                            NOT NULL,
    created_at  DATETIME(6)                                    NOT NULL,
    updated_at  DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feed_clothes (feed_id, clothes_id),
    UNIQUE KEY uk_feed_clothes_order (feed_id, order_index),
    KEY idx_feed_clothes_clothes (clothes_id),
    CONSTRAINT fk_feed_clothes_feed FOREIGN KEY (feed_id) REFERENCES feeds (id) ON DELETE CASCADE,
    -- 피드에 쓰인 옷은 지울 수 없다. 지우면 과거 착장이 사라진다.
    CONSTRAINT fk_feed_clothes_clothes FOREIGN KEY (clothes_id)
        REFERENCES clothes (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '피드에 포함된 의상';


CREATE TABLE feed_likes
(
    id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    feed_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6)                                    NOT NULL,
    updated_at DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    -- 동시 요청으로 중복 좋아요가 들어가는 것을 DB 에서 막는다
    UNIQUE KEY uk_feed_likes_feed_user (feed_id, user_id),
    KEY idx_feed_likes_user (user_id, feed_id), -- likedByMe 판정
    CONSTRAINT fk_feed_likes_feed FOREIGN KEY (feed_id) REFERENCES feeds (id) ON DELETE CASCADE,
    CONSTRAINT fk_feed_likes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '피드 좋아요';


CREATE TABLE comments
(
    id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    feed_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    author_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content    TEXT                                           NOT NULL,
    created_at DATETIME(6)                                    NOT NULL,
    updated_at DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_comments_feed (feed_id, created_at, id),
    KEY idx_comments_author (author_id),
    CONSTRAINT fk_comments_feed FOREIGN KEY (feed_id) REFERENCES feeds (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '피드 댓글';


CREATE TABLE follows
(
    id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    follower_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    followee_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at  DATETIME(6)                                    NOT NULL,
    updated_at  DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_follows_follower_followee (follower_id, followee_id),
    KEY idx_follows_followee (followee_id, id),
    KEY idx_follows_follower (follower_id, id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '팔로우';


-- ── 알림 · DM (양정우) ───────────────────────────────────────────────────────

CREATE TABLE notifications
(
    id                CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    receiver_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, -- 시스템 알림이면 NULL
    type              VARCHAR(32)                                    NOT NULL,
    -- 가리키는 대상의 종류가 여러 개라 FK 를 걸지 않는다
    related_entity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    title             VARCHAR(200)                                   NOT NULL,
    content           VARCHAR(1000)                                  NOT NULL,
    level             VARCHAR(16)                                    NOT NULL,
    created_at        DATETIME(6)                                    NOT NULL,
    updated_at        DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_receiver (receiver_id, created_at, id),
    CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_notifications_level CHECK (level IN ('INFO', 'WARNING', 'ERROR')),
    CONSTRAINT ck_notifications_type CHECK (type IN
                                            ('ROLE_CHANGED', 'CLOTHES_ATTRIBUTE_ADDED', 'FEED_LIKED',
                                             'FEED_COMMENTED', 'FOLLOW_CREATED', 'FEED_CREATED',
                                             'DM_RECEIVED', 'VIRTUAL_TRY_ON_COMPLETED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '알림';
-- 읽음 처리는 실제 삭제다. 읽음 플래그를 두지 않는다.


CREATE TABLE direct_messages
(
    id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    -- 두 사용자 id 를 문자열 정렬해 작은 쪽을 앞에 붙인 대화방 키 (36+1+36).
    -- 프론트 STOMP 목적지 /sub/direct-messages_{작은id}_{큰id} 와 규칙이 같아야 한다.
    dm_key      CHAR(73) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sender_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    receiver_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content     VARCHAR(1000)                                  NOT NULL,
    created_at  DATETIME(6)                                    NOT NULL,
    updated_at  DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_dm_key_created (dm_key, created_at, id),
    CONSTRAINT fk_dm_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dm_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT 'DM 메시지';


-- ── 가상 피팅 (양정우 + 여운정) ──────────────────────────────────────────────

CREATE TABLE virtual_try_on_caches
(
    id                    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin      NOT NULL,
    requester_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin      NOT NULL,
    -- 재생성 차단 키. (모델 해시 + 정렬한 의상 id) 로 앱에서 만든다.
    -- 개별 컬럼 조합으로 UNIQUE 를 걸면 additional_clothes_id 가 NULL 일 때 중복이 쌓인다.
    cache_key             VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL,
    model_hash            VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL,
    top_clothes_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin      NOT NULL,
    bottom_clothes_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin      NOT NULL,
    additional_clothes_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin      NULL,
    parent_cache_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin      NULL, -- 최초 생성은 부모가 없다
    generation_depth      TINYINT                                            NOT NULL DEFAULT 1,
    result_image_key      VARCHAR(500)                                       NOT NULL,
    created_at            DATETIME(6)                                        NOT NULL,
    updated_at            DATETIME(6)                                        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_try_on_cache_key (cache_key),
    KEY idx_try_on_cache_requester (requester_id, created_at, id),
    CONSTRAINT fk_try_on_cache_requester FOREIGN KEY (requester_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_try_on_cache_top FOREIGN KEY (top_clothes_id)
        REFERENCES clothes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_try_on_cache_bottom FOREIGN KEY (bottom_clothes_id)
        REFERENCES clothes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_try_on_cache_additional FOREIGN KEY (additional_clothes_id)
        REFERENCES clothes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_try_on_cache_parent FOREIGN KEY (parent_cache_id)
        REFERENCES virtual_try_on_caches (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '가상 피팅 결과 캐시';


CREATE TABLE virtual_try_on_jobs
(
    id                    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requester_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status                VARCHAR(16)                                    NOT NULL,
    fashn_prediction_id   VARCHAR(100)                                   NULL, -- 외부 피팅 API 예측 id
    current_step          VARCHAR(32)                                    NOT NULL,
    model_image_key       VARCHAR(500)                                   NULL,
    top_clothes_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    bottom_clothes_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    additional_clothes_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_cache_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at            DATETIME(6)                                    NOT NULL,
    updated_at            DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_try_on_requester (requester_id, created_at, id),
    KEY idx_try_on_status (status, created_at), -- 진행 중인 작업 폴링
    KEY idx_try_on_prediction (fashn_prediction_id),
    CONSTRAINT fk_try_on_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_try_on_top FOREIGN KEY (top_clothes_id) REFERENCES clothes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_try_on_bottom FOREIGN KEY (bottom_clothes_id)
        REFERENCES clothes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_try_on_additional FOREIGN KEY (additional_clothes_id)
        REFERENCES clothes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_try_on_result_cache FOREIGN KEY (result_cache_id)
        REFERENCES virtual_try_on_caches (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT '가상 피팅 작업';
