package com.otboo.feed.entity;

import com.otboo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 피드에 포함된 의상 한 벌.
 *
 * <p>{@code Clothes} 엔티티는 의상 파트 소유라 id 만 들고 있는다. FK 는 DB 에 걸려 있고
 * ({@code ON DELETE RESTRICT}) 이름·이미지·속성은 조회 전용 SQL 로 채운다.
 */
@Entity
@Getter
@Table(name = "feed_clothes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedClothes extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_id", nullable = false, updatable = false)
    private Feed feed;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "clothes_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID clothesId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    static FeedClothes of(Feed feed, UUID clothesId, int orderIndex) {
        FeedClothes feedClothes = new FeedClothes();
        feedClothes.feed = feed;
        feedClothes.clothesId = clothesId;
        feedClothes.orderIndex = orderIndex;
        return feedClothes;
    }
}
