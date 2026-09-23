package com.otboo.feed.query;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드가 참조하는 다른 파트의 데이터(날씨·의상)가 실제로 있는지 확인
 *
 * <h2>why advanced search instead of FK</h2>
 * DB 의 FK 가 정합성은 지켜주지만, 위반하면 {@code DataIntegrityViolationException} 이 올라와
 * 사용자에게 <b>500 과 "제약조건 위반" 같은 메시지</b>가 나간다. 어떤 옷이 문제인지도 알 수 없다.
 * 미리 확인해서 "해당 의상을 찾을 수 없습니다 (clothesId=...)" 로 돌려준다.
 *
 * <p>확인과 INSERT 사이에 그 행이 지워질 수는 있다. 그건 FK 가 막아주므로 이중 방어다.
 */
@Component
@RequiredArgsConstructor
public class FeedReferenceQuery {

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public boolean weatherExists(UUID weatherId) {
        Integer found = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM weathers WHERE id = :id)",
                new MapSqlParameterSource("id", weatherId.toString()), Integer.class);
        return found != null && found == 1;
    }

    /**
     * @return 의상 id → 소유자 id. 존재하지 않는 의상은 결과 x
     *         "없는 옷" 과 "남의 옷" 을 한 번의 쿼리로 구분용
     */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> findClothesOwners(Collection<UUID> clothesIds) {
        if (clothesIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> owners = new LinkedHashMap<>();
        jdbc.query("SELECT id, owner_id FROM clothes WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", clothesIds.stream().map(UUID::toString).toList()),
                rs -> {
                    owners.put(UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("owner_id")));
                });
        return owners;
    }
}
