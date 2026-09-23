package com.otboo.feed.entity;

import com.otboo.common.entity.BaseEntity;
import com.otboo.user.entity.User;
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

/** 피드 댓글. 대댓글은 스펙에 없다 — 부모 참조를 두지 않는다. */
@Entity
@Getter
@Table(name = "comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_id", nullable = false, updatable = false)
    private Feed feed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    private Comment(Feed feed, User author, String content) {
        this.feed = feed;
        this.author = author;
        this.content = content;
    }

    public static Comment create(Feed feed, User author, String content) {
        return new Comment(feed, author, content);
    }

    public boolean isAuthor(UUID userId) {
        return author.getId().equals(userId);
    }
}
