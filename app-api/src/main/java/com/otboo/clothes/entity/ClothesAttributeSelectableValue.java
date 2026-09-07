package com.otboo.clothes.entity;

import com.otboo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "clothes_attribute_selectable_values")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothesAttributeSelectableValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private ClothesAttributeDefinition definition;

    @Column(name = "value", nullable = false, length = 100)
    private String value;

    private ClothesAttributeSelectableValue(
            ClothesAttributeDefinition definition,
            String value
    ) {
        this.definition = definition;
        this.value = value;
    }

    static ClothesAttributeSelectableValue create(
            ClothesAttributeDefinition definition,
            String value
    ) {
        return new ClothesAttributeSelectableValue(definition, value);
    }

    void changeValue(String value) {
        this.value = value;
    }
}
