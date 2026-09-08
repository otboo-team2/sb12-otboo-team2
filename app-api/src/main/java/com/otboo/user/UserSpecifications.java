package com.otboo.user;

import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.SortDirection;
import com.otboo.user.dto.UserSearchCondition;
import com.otboo.user.entity.User;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * 계정 목록 조회 조건을 조립한다.
 *
 * <p>필터와 커서를 분리해둔 이유 — {@code totalCount} 는 <b>필터에만</b> 걸린 전체 건수여야 한다.
 * 커서 조건까지 넣고 세면 두 번째 페이지부터 총 개수가 줄어든다.
 */
final class UserSpecifications {

    private UserSpecifications() {
    }

    static Specification<User> filter(UserSearchCondition condition) {
        return (root, query, builder) -> {
            var predicates = builder.conjunction();
            if (condition.emailLike() != null) {
                predicates = builder.and(predicates, builder.like(
                        builder.lower(root.get("email")),
                        "%" + condition.emailLike().toLowerCase() + "%"));
            }
            if (condition.roleEqual() != null) {
                predicates = builder.and(predicates,
                        builder.equal(root.get("role"), condition.roleEqual()));
            }
            if (condition.locked() != null) {
                predicates = builder.and(predicates,
                        builder.equal(root.get("locked"), condition.locked()));
            }
            return predicates;
        };
    }

    /**
     * keyset 조건. 정렬 키가 같은 행이 있어도 순서가 흔들리지 않도록 id 를 tiebreaker 로 함께 본다.
     *
     * <pre>
     * (정렬키 &lt; :cursor) OR (정렬키 = :cursor AND id &lt; :idAfter)   -- 내림차순
     * </pre>
     */
    static Specification<User> afterCursor(CursorRequest request, UserSortField sortField) {
        if (request.isFirstPage() || request.idAfter() == null) {
            return (root, query, builder) -> null;      // 첫 페이지는 커서 조건이 없다
        }
        boolean ascending = request.sortDirection() == SortDirection.ASCENDING;
        UUID idAfter = request.idAfter();

        // 정렬 필드가 둘뿐이라 타입별로 나눠 쓴다. 제네릭으로 묶으면 캐스팅만 늘고 읽기 어려워진다.
        return (root, query, builder) -> {
            Predicate beyondKey;
            Predicate sameKey;
            if (sortField == UserSortField.EMAIL) {
                Path<String> key = root.get("email");
                String cursorValue = CursorCodec.asString(request.cursor());
                beyondKey = ascending
                        ? builder.greaterThan(key, cursorValue)
                        : builder.lessThan(key, cursorValue);
                sameKey = builder.equal(key, cursorValue);
            } else {
                Path<Instant> key = root.get("createdAt");
                Instant cursorValue = CursorCodec.asInstant(request.cursor());
                beyondKey = ascending
                        ? builder.greaterThan(key, cursorValue)
                        : builder.lessThan(key, cursorValue);
                sameKey = builder.equal(key, cursorValue);
            }

            Path<UUID> id = root.get("id");
            Predicate beyondId = ascending
                    ? builder.greaterThan(id, idAfter)
                    : builder.lessThan(id, idAfter);

            return builder.or(beyondKey, builder.and(sameKey, beyondId));
        };
    }

    /** 허용된 정렬 필드. 임의의 문자열을 받으면 매핑되지 않은 속성으로 쿼리가 터진다. */
    enum UserSortField {

        EMAIL("email"),
        CREATED_AT("createdAt");

        private final String attribute;

        UserSortField(String attribute) {
            this.attribute = attribute;
        }

        String attribute() {
            return attribute;
        }
    }
}
