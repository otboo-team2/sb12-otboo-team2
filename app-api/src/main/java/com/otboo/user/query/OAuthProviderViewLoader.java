package com.otboo.user.query;

import com.otboo.user.entity.OAuthProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 계정 목록 응답에 연결된 소셜 제공자를 한 번에 채운다. */
@Repository
public class OAuthProviderViewLoader {

    private final NamedParameterJdbcTemplate jdbc;

    public OAuthProviderViewLoader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, List<String>> load(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        List<UserProvider> rows = jdbc.query("""
                        select user_id, provider
                        from user_oauth_accounts
                        where user_id in (:userIds)
                        order by user_id, provider
                        """,
                new MapSqlParameterSource("userIds", userIds.stream()
                        .map(UUID::toString)
                        .toList()),
                (rs, rowNum) -> new UserProvider(
                        UUID.fromString(rs.getString("user_id")),
                        OAuthProvider.valueOf(rs.getString("provider")).code()));

        return rows.stream().collect(Collectors.groupingBy(
                UserProvider::userId,
                Collectors.mapping(UserProvider::provider, Collectors.toList())));
    }

    private record UserProvider(UUID userId, String provider) {
    }
}
