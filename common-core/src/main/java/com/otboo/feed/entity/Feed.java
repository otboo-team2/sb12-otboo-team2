package com.otboo.feed.entity;

import com.otboo.common.entity.BaseEntity;
import com.otboo.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 피드(OOTD).
 *
 * <h2>weatherId · recommendationId 를 연관관계로 두지 않은 이유</h2>
 * {@code Weather}·{@code RecommendationHistory} 엔티티는 날씨·추천 파트 소유다. 아직 없는 클래스를
 * 참조하면 피드 파트가 그 파트의 진행에 묶인다. FK 는 DB(V1)에 이미 걸려 있어 정합성은 보장되고,
 * 응답에 필요한 날씨 요약은 조회 전용 SQL(FeedViewLoader)로 한 번에 읽는다.
 *
 * <h2>likeCount · commentCount 는 비정규화 컬럼이다</h2>
 * 목록마다 {@code COUNT(*)} 를 돌리면 피드 20건에 집계 쿼리가 40번 붙는다. 또한 스펙상
 * {@code sortBy=likeCount} 정렬이 있어 <b>정렬 키가 컬럼으로 존재해야</b> 인덱스를 탈 수 있다.
 *
 * <p>대신 값이 어긋나지 않게 <b>세터를 열지 않는다.</b> 증감은 반드시
 * {@code FeedRepository} 의 원자적 UPDATE 로만 한다. 엔티티에서 {@code count++} 를 하면
 * 동시에 좋아요를 누른 두 요청 중 하나가 사라진다(lost update).
 */
@Entity
@Getter
@Table(name = "feeds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feed extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "weather_id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID weatherId;

    /** AI 추천을 거쳐 올린 피드만 값이 있다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "recommendation_id", updatable = false, columnDefinition = "CHAR(36)")
    private UUID recommendationId;

    /** TEXT 컬럼. 기본 매핑(VARCHAR)으로 두면 {@code ddl-auto: validate} 가 타입 불일치로 막는다. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    /**
     * 착장 목록. 피드가 지워지면 함께 지워진다({@code orphanRemoval}).
     * {@code order_index} 순서가 곧 화면 노출 순서다.
     */
    @OneToMany(mappedBy = "feed", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private final List<FeedClothes> clothes = new ArrayList<>();

    private Feed(User author, UUID weatherId, UUID recommendationId, String content) {
        this.author = author;
        this.weatherId = weatherId;
        this.recommendationId = recommendationId;
        this.content = content;
        this.likeCount = 0L;
        this.commentCount = 0;
    }

    public static Feed create(User author, UUID weatherId, String content, List<UUID> clothesIds) {
        Feed feed = new Feed(author, weatherId, null, content);
        for (int i = 0; i < clothesIds.size(); i++) {
            feed.clothes.add(FeedClothes.of(feed, clothesIds.get(i), i));
        }
        return feed;
    }

    /** 스펙상 수정 가능한 필드는 content 하나뿐이다(FeedUpdateRequest). */
    public void updateContent(String content) {
        this.content = content;
    }

    /**
     * ⚠️ 지우지 말 것. 지금 호출부가 없어 보이지만, 이 메서드가 없으면 {@code @Getter} 가
     * <b>수정 가능한 리스트를 그대로 노출하는</b> getter 를 대신 만들어낸다.
     * 그러면 바깥에서 {@code getClothes().add(...)} 로 착장을 끼워 넣을 수 있고,
     * {@code order_index} 가 깨진 채 저장된다.
     */
    public List<FeedClothes> getClothes() {
        return Collections.unmodifiableList(clothes);
    }

    public boolean isAuthor(UUID userId) {
        return author.getId().equals(userId);
    }
}
