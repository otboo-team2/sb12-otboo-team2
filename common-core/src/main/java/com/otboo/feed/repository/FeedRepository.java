package com.otboo.feed.repository;

import com.otboo.feed.entity.Feed;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

    /** 권한 검사에 author 가 필요하다. 지연로딩으로 두면 매번 추가 쿼리가 나간다. */
    @Query("select f from Feed f join fetch f.author where f.id = :id")
    Optional<Feed> findWithAuthorById(@Param("id") UUID id);

    /**
     * 카운터 증감은 전부 원자적 UPDATE 로 한다.
     *
     * <p>{@code feed.setLikeCount(feed.getLikeCount() + 1)} 은 읽은 값 위에 덮어쓰기라
     * 동시에 좋아요를 누른 두 요청 중 하나가 사라진다. DB 가 현재 값을 기준으로 더하게 해야 한다.
     *
     * <p>{@code clearAutomatically} 로 영속성 컨텍스트를 비운다. 안 그러면 UPDATE 이후에도
     * 1차 캐시에 남은 옛 값이 응답에 실린다.
     *
     * <p>반환 타입은 {@code void} 다. {@code @Modifying} 은 영향받은 행 수를 줄 수 있지만
     * 호출부에서 쓰지 않는다. 감소 쿼리의 0 은 "이미 0이라 더 못 내렸다" 는 뜻인데,
     * 그 조건 자체가 음수를 막는 장치라 따로 처리할 일이 없다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Feed f set f.likeCount = f.likeCount + 1 where f.id = :id")
    void increaseLikeCount(@Param("id") UUID id);

    /** 음수 방지 조건을 DB 에 둔다. 중복 취소 요청이 와도 0 아래로 내려가지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Feed f set f.likeCount = f.likeCount - 1 where f.id = :id and f.likeCount > 0")
    void decreaseLikeCount(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Feed f set f.commentCount = f.commentCount + 1 where f.id = :id")
    void increaseCommentCount(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Feed f set f.commentCount = f.commentCount - 1 where f.id = :id and f.commentCount > 0")
    void decreaseCommentCount(@Param("id") UUID id);
}
