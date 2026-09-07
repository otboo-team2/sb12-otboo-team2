package com.otboo.clothes.entity;

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

@Getter
@Entity
@Table(name = "clothes_attribute_values")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothesAttributeValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clothes_id", nullable = false)
    private Clothes clothes;

    /**
     * 정의와 선택값은 UUID로 보관한다. 두 컬럼의 조합은 V1의 복합 외래 키가 검증한다.
     * 따라서 속성 정의·선택값 엔티티를 이 엔티티에 직접 연결하지 않아도 사용자 도메인과 결합되지 않는다.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "definition_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID definitionId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "selectable_value_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID selectableValueId;

    private ClothesAttributeValue(
            Clothes clothes,
            UUID definitionId,
            UUID selectableValueId
    ) {
        this.clothes = clothes;
        this.definitionId = definitionId;
        this.selectableValueId = selectableValueId;
    }

    public static ClothesAttributeValue create(
            Clothes clothes,
            UUID definitionId,
            UUID selectableValueId
    ) {
        return new ClothesAttributeValue(clothes, definitionId, selectableValueId);
    }

    void changeSelectableValueId(UUID selectableValueId) {
        this.selectableValueId = selectableValueId;
    }
}
