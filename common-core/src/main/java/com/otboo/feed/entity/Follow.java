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
 * 팔로우.
 * "팔로워 수" 는 {@code followee_id} 로 세고, "팔로잉 수" 는 {@code follower_id} 로 센다.
 *
 * <p>중복 방지는 좋아요와 같은 이유로 DB 의 {@code uk_follows_follower_followee} 가 한다.
 * "먼저 조회하고 없으면 저장" 은 동시 요청 두 개가 모두 통과한다.
 *
 * <p>자기 자신 팔로우는 {@code ck_follows_not_self} 가 막지만 그대로 두면 500 이 나가므로
 * 서비스에서 먼저 걸러 400 으로 돌려준다.
 */
@Entity
@Getter
@Table(name = "follows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseEntity {

    /** 팔로우 <b>하는</b> 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false, updatable = false)
    private User follower;

    /** 팔로우 <b>당하는</b> 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followee_id", nullable = false, updatable = false)
    private User followee;

    private Follow(User follower, User followee) {
        this.follower = follower;
        this.followee = followee;
    }

    public static Follow of(User follower, User followee) {
        return new Follow(follower, followee);
    }
}
