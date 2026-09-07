package com.otboo.feed.query;

import static com.otboo.feed.query.JdbcColumns.decimal;
import static com.otboo.feed.query.JdbcColumns.enumOf;
import static com.otboo.feed.query.JdbcColumns.instant;
import static com.otboo.feed.query.JdbcColumns.uuid;

import com.otboo.feed.dto.AuthorDto;
import com.otboo.feed.dto.ClothesAttributeWithDefDto;
import com.otboo.feed.dto.FeedDto;
import com.otboo.feed.dto.OotdDto;
import com.otboo.feed.dto.PrecipitationDto;
import com.otboo.feed.dto.TemperatureDto;
import com.otboo.feed.dto.WeatherSummaryDto;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.SkyStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드 id 목록을 완성된 {@link FeedDto} 목록 변경
 *
 * <h2>Why SQL</h2>
 * {@code feeds · users · profiles · weathers · feed_clothes+clothes · 의상 속성 3종}.
 * 이걸 엔티티 그래프로 타면 피드 20건에 수십 번의 추가 쿼리가 붙고(N+1),
 * 그렇다고 전부 join fetch 하면 컬렉션이 둘 이상이라 카테시안 곱이 난다.
 *
 * <p>중요한 점<b>날씨·의상 엔티티가 다른 파트 소유</b>라는 점이다. 조회 전용 SQL 로 읽으면
 * 그 파트들의 진행 상황과 무관하게 피드 API 를 완성할 수 있고, 나중에 엔티티가 생겨도
 * 이 클래스만 그대로 두면 된다.
 *
 * <p><b>쿼리 수는 페이지 크기와 무관하게 고정</b>이다(최대 5회).
 */
@Component
@RequiredArgsConstructor
public class FeedViewLoader {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String FEED_SQL = """
            SELECT f.id                                 AS feed_id,
                   f.created_at                         AS created_at,
                   f.updated_at                         AS updated_at,
                   f.content                            AS content,
                   f.like_count                         AS like_count,
                   f.comment_count                      AS comment_count,
                   u.id                                 AS author_id,
                   u.name                               AS author_name,
                   p.profile_image_url                  AS author_image_url,
                   w.id                                 AS weather_id,
                   w.sky_status                         AS sky_status,
                   w.precipitation_type                 AS precipitation_type,
                   w.precipitation_amount               AS precipitation_amount,
                   w.precipitation_probability          AS precipitation_probability,
                   w.temperature_current                AS temperature_current,
                   w.temperature_compared_to_day_before AS temperature_compared,
                   w.temperature_min                    AS temperature_min,
                   w.temperature_max                    AS temperature_max
            FROM feeds f
                     JOIN users u ON u.id = f.author_id
                     LEFT JOIN profiles p ON p.user_id = u.id
                     JOIN weathers w ON w.id = f.weather_id
            WHERE f.id IN (:ids)
            """;

    private static final String OOTD_SQL = """
            SELECT fc.feed_id     AS feed_id,
                   c.id           AS clothes_id,
                   c.name         AS clothes_name,
                   c.image_url    AS image_url,
                   c.type         AS clothes_type
            FROM feed_clothes fc
                     JOIN clothes c ON c.id = fc.clothes_id
            WHERE fc.feed_id IN (:ids)
            ORDER BY fc.feed_id, fc.order_index
            """;

    private static final String ATTRIBUTE_SQL = """
            SELECT cav.clothes_id AS clothes_id,
                   d.id           AS definition_id,
                   d.name         AS definition_name,
                   sv.value       AS value
            FROM clothes_attribute_values cav
                     JOIN clothes_attribute_definitions d ON d.id = cav.definition_id
                     JOIN clothes_attribute_selectable_values sv
                          ON sv.id = cav.selectable_value_id
            WHERE cav.clothes_id IN (:clothesIds)
            ORDER BY d.created_at, d.id
            """;

    private static final String SELECTABLE_SQL = """
            SELECT definition_id, value
            FROM clothes_attribute_selectable_values
            WHERE definition_id IN (:definitionIds)
            ORDER BY definition_id, value
            """;

    private static final String LIKED_SQL = """
            SELECT feed_id
            FROM feed_likes
            WHERE user_id = :viewerId AND feed_id IN (:ids)
            """;

    /**
     * @param orderedIds 검색이 정한 순서. <b>이 순서를 그대로 유지한다.</b>
     *                   {@code IN} 절의 결과 순서는 보장되지 않으므로 여기서 다시 정렬한다.
     * @param viewerId   비로그인 조회면 {@code null}
     */
    @Transactional(readOnly = true)
    public List<FeedDto> load(List<UUID> orderedIds, UUID viewerId) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = toStrings(orderedIds);

        Map<UUID, FeedRow> rows = new LinkedHashMap<>();
        jdbc.query(FEED_SQL, new MapSqlParameterSource("ids", ids), rs -> {
            FeedRow row = mapFeedRow(rs);
            rows.put(row.id(), row);
        });

        Map<UUID, List<OotdDto>> ootds = loadOotds(ids);
        Set<UUID> likedFeedIds = loadLikedFeedIds(ids, viewerId);

        List<FeedDto> result = new ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            FeedRow row = rows.get(id);
            if (row == null) {
                // 지금은 검색과 이 조회가 같은 읽기 트랜잭션 안에서 같은 스냅샷을 보므로 도달하지 않는다.
                // 검색을 외부 엔진으로 옮기면(색인이 원본보다 늦을 수 있다) 그때부터 의미가 생긴다.
                continue;
            }
            result.add(new FeedDto(
                    row.id(), row.createdAt(), row.updatedAt(), row.author(), row.weather(),
                    ootds.getOrDefault(id, List.of()), row.content(),
                    row.likeCount(), row.commentCount(), likedFeedIds.contains(id)
            ));
        }
        return result;
    }

    /** 단건 응답(등록·수정·좋아요)용. 목록과 같은 경로를 타야 응답 모양이 어긋나지 않는다. */
    @Transactional(readOnly = true)
    public FeedDto loadOne(UUID feedId, UUID viewerId) {
        List<FeedDto> feeds = load(List.of(feedId), viewerId);
        return feeds.isEmpty() ? null : feeds.getFirst();
    }

    private Map<UUID, List<OotdDto>> loadOotds(List<String> feedIds) {
        record OotdRow(UUID feedId, UUID clothesId, String name, String imageUrl, String type) {
        }

        List<OotdRow> ootdRows = jdbc.query(OOTD_SQL, new MapSqlParameterSource("ids", feedIds),
                (rs, i) -> new OotdRow(
                        uuid(rs, "feed_id"), uuid(rs, "clothes_id"),
                        rs.getString("clothes_name"), rs.getString("image_url"),
                        rs.getString("clothes_type")));

        if (ootdRows.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<ClothesAttributeWithDefDto>> attributes = loadAttributes(
                ootdRows.stream().map(OotdRow::clothesId).distinct().toList());

        Map<UUID, List<OotdDto>> byFeed = new LinkedHashMap<>();
        for (OotdRow row : ootdRows) {
            byFeed.computeIfAbsent(row.feedId(), key -> new ArrayList<>())
                    .add(new OotdDto(row.clothesId(), row.name(), row.imageUrl(), row.type(),
                            attributes.getOrDefault(row.clothesId(), List.of())));
        }
        return byFeed;
    }

    private Map<UUID, List<ClothesAttributeWithDefDto>> loadAttributes(List<UUID> clothesIds) {
        record AttributeRow(UUID clothesId, UUID definitionId, String definitionName, String value) {
        }

        List<AttributeRow> attributeRows = jdbc.query(ATTRIBUTE_SQL,
                new MapSqlParameterSource("clothesIds", toStrings(clothesIds)),
                (rs, i) -> new AttributeRow(uuid(rs, "clothes_id"), uuid(rs, "definition_id"),
                        rs.getString("definition_name"), rs.getString("value")));

        if (attributeRows.isEmpty()) {
            return Map.of();
        }

        // 프론트가 수정 폼의 선택지로 쓰므로 정의별 선택 가능한 값 전체가 함께 나가야 한다.
        Map<UUID, List<String>> selectables = new LinkedHashMap<>();
        jdbc.query(SELECTABLE_SQL,
                new MapSqlParameterSource("definitionIds", toStrings(
                        attributeRows.stream().map(AttributeRow::definitionId).distinct().toList())),
                rs -> {
                    selectables.computeIfAbsent(uuid(rs, "definition_id"), key -> new ArrayList<>())
                            .add(rs.getString("value"));
                });

        Map<UUID, List<ClothesAttributeWithDefDto>> byClothes = new LinkedHashMap<>();
        for (AttributeRow row : attributeRows) {
            byClothes.computeIfAbsent(row.clothesId(), key -> new ArrayList<>())
                    .add(new ClothesAttributeWithDefDto(
                            row.definitionId(), row.definitionName(),
                            selectables.getOrDefault(row.definitionId(), List.of()), row.value()));
        }
        return byClothes;
    }

    private Set<UUID> loadLikedFeedIds(List<String> feedIds, UUID viewerId) {
        if (viewerId == null) {
            return Set.of();
        }
        Set<UUID> liked = new LinkedHashSet<>();
        jdbc.query(LIKED_SQL,
                new MapSqlParameterSource("ids", feedIds).addValue("viewerId", viewerId.toString()),
                rs -> {
                    liked.add(uuid(rs, "feed_id"));
                });
        return liked;
    }

    private FeedRow mapFeedRow(ResultSet rs) throws SQLException {
        return new FeedRow(
                uuid(rs, "feed_id"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getString("content"),
                rs.getLong("like_count"),
                rs.getInt("comment_count"),
                new AuthorDto(uuid(rs, "author_id"), rs.getString("author_name"),
                        rs.getString("author_image_url")),
                new WeatherSummaryDto(
                        uuid(rs, "weather_id"),
                        enumOf(rs, "sky_status", SkyStatus.class),
                        new PrecipitationDto(
                                enumOf(rs, "precipitation_type", PrecipitationType.class),
                                decimal(rs, "precipitation_amount"),
                                decimal(rs, "precipitation_probability")),
                        new TemperatureDto(
                                decimal(rs, "temperature_current"),
                                decimal(rs, "temperature_compared"),
                                decimal(rs, "temperature_min"),
                                decimal(rs, "temperature_max")))
        );
    }

    private record FeedRow(
            UUID id, Instant createdAt, Instant updatedAt, String content,
            long likeCount, int commentCount, AuthorDto author, WeatherSummaryDto weather
    ) {
    }

    private static List<String> toStrings(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).toList();
    }
}
