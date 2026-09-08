package com.otboo.feed.repository;

import com.otboo.feed.entity.Follow;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    /**
     * 저장 전 빠른 실패용. 진짜 중복 방어는 {@code uk_follows_follower_followee} 위반을 잡는 쪽이다.
     * 이 확인과 저장 사이에 다른 요청이 먼저 넣을 수 있다.
     */
    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    /**
     * 주인 확인용. 엔티티를 통째로 읽지 않고 {@code follower_id} 만 꺼낸다.
     * 404 와 403 을 구분하려면 존재 여부만으로는 부족해서 주인이 누구인지까지 필요하다.
     */
    @Query("select f.follower.id from Follow f where f.id = :followId")
    Optional<UUID> findFollowerIdById(@Param("followId") UUID followId);

    /**
     * 팔로우 취소.
     *
     * <p><b>{@code delete(entity)} 를 쓰면 안 된다.</b> 읽고 나서 지우기 전에 다른 요청이 먼저
     * 지우면 Hibernate 가 "지워질 행이 없다" 로 {@code ObjectOptimisticLockingFailureException}
     * 을 던지고, 그건 핸들러에서 <b>500</b> 이 된다. 버튼을 빠르게 두 번 누르면 재현된다.
     *
     * <p>한 문장으로 지우고 <b>지워진 행 수로 판단</b>하면 그 창이 아예 없다.
     * {@code follower_id} 조건을 같이 걸어 남의 팔로우는 애초에 지워지지 않는다.
     *
     * @return 실제로 지워진 행 수. 0 이면 그 사이 이미 사라졌다.
     */
    @Modifying
    @Query("delete from Follow f where f.id = :followId and f.follower.id = :followerId")
    long deleteByIdAndFollowerId(@Param("followId") UUID followId,
            @Param("followerId") UUID followerId);
}
