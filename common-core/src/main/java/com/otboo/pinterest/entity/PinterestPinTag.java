package com.otboo.pinterest.entity;

import com.otboo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 핀 하나에 붙은 style · item 태그 한 개.
 *
 * <p>MySQL 에는 배열 컬럼이 없다. JSON 컬럼 + multi-valued index 도 가능하지만
 * "style 이 minimal 인 핀" 같은 조회를 팀원 누구나 평범한 JOIN 으로 읽을 수 있게 자식 테이블로 둔다.
 */
@Entity
@Getter
@Table(name = "pinterest_pin_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PinterestPinTag extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pinterest_pin_id", nullable = false, updatable = false)
    private PinterestPin pin;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false, updatable = false, length = 16)
    private PinTagType tagType;

    /** enum 이름을 그대로 저장한다({@code MINIMAL}). description 표기({@code minimal})가 아니다. */
    @Column(name = "tag_value", nullable = false, updatable = false, length = 32)
    private String tagValue;

    PinterestPinTag(PinterestPin pin, PinTagType tagType, String tagValue) {
        this.pin = pin;
        this.tagType = tagType;
        this.tagValue = tagValue;
    }

    boolean matches(PinTagType type, String value) {
        return tagType == type && tagValue.equals(value);
    }
}
