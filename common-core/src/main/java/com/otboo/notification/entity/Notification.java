package com.otboo.notification.entity;

import com.otboo.common.entity.BaseEntity;
import com.otboo.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "related_entity_id")
    private String relatedEntityId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationLevel level;

    private Notification(User receiver, User actor,
                         NotificationType type, String relatedEntityId,
                         String title, String content,
                         NotificationLevel level) {
        this.receiver = receiver;
        this.actor = actor;
        this.type = type;
        this.relatedEntityId = relatedEntityId;
        this.title = title;
        this.content = content;
        this.level = level;
    }

    public static Notification create(User receiver, User actor,
                                      NotificationType type, String relatedEntityId,
                                      String title, String content,
                                      NotificationLevel level) {
        return new Notification(receiver, actor, type, relatedEntityId, title, content, level);
    }
}
