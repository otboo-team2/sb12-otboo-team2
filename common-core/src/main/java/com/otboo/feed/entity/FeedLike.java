package com.otboo.feed.entity;

import com.otboo.common.entity.BaseEntity;
import com.otboo.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 피드 좋아요.
 *
 * <p>중복 방지는 애플리케이션이 아니라 DB 의 {@code uk_feed_likes_feed_user} 가 한다.
 * "먼저 조회하고 없으면 저장" 은 동시 요청 두 개가 모두 통과한다.
 * 저장 시도 후 {@code DataIntegrityViolationException} 을 잡는 쪽이 정확하다.
 */
@Entity
@Getter
@Table(name = "feed_likes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedLike extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_id", nullable = false, updatable = false)
    private Feed feed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    private FeedLike(Feed feed, User user) {
        this.feed = feed;
        this.user = user;
    }

    public static FeedLike of(Feed feed, User user) {
        return new FeedLike(feed, user);
    }
}
