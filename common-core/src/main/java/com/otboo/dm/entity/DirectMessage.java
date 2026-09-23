package com.otboo.dm.entity;

import com.otboo.common.entity.BaseEntity;
import com.otboo.dm.util.DmKeyGenerator;
import com.otboo.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "direct_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage extends BaseEntity {

    @Column(name = "dm_key", nullable = false, columnDefinition = "CHAR(73)")
    private String dmKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(nullable = false, length = 1000)
    private String content;

    private DirectMessage(String dmKey, User sender, User receiver, String content) {
        this.dmKey = dmKey;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
    }

    public static DirectMessage create(User sender, User receiver, String content) {
        String dmKey = DmKeyGenerator.generate(sender.getId(), receiver.getId());
        return new DirectMessage(dmKey, sender, receiver, content);
    }
}
