package com.otboo.clothes.entity;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.entity.BaseEntity;
import com.otboo.common.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "clothes_attribute_definitions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothesAttributeDefinition extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @OneToMany(
            mappedBy = "definition",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("createdAt ASC, id ASC")
    private final List<ClothesAttributeSelectableValue> selectableValues = new ArrayList<>();

    private ClothesAttributeDefinition(String name, List<String> selectableValues) {
        this.name = normalizeName(name);
        normalizeSelectableValues(selectableValues).stream()
                .map(value -> ClothesAttributeSelectableValue.create(this, value))
                .forEach(this.selectableValues::add);
    }

    public static ClothesAttributeDefinition create(String name, List<String> selectableValues) {
        return new ClothesAttributeDefinition(name, selectableValues);
    }

    public List<ClothesAttributeSelectableValue> getSelectableValues() {
        return Collections.unmodifiableList(selectableValues);
    }

    public void changeName(String name) {
        this.name = normalizeName(name);
    }

    public void replaceSelectableValues(List<String> selectableValues) {
        List<String> normalizedValues = normalizeSelectableValues(selectableValues);
        Map<String, ClothesAttributeSelectableValue> existingValues = new LinkedHashMap<>();
        this.selectableValues.forEach(value -> existingValues.put(
                value.getValue().toLowerCase(Locale.ROOT), value));

        List<ClothesAttributeSelectableValue> synchronizedValues = new ArrayList<>();
        for (String value : normalizedValues) {
            ClothesAttributeSelectableValue existing = existingValues.remove(
                    value.toLowerCase(Locale.ROOT));
            if (existing == null) {
                synchronizedValues.add(ClothesAttributeSelectableValue.create(this, value));
            } else {
                existing.changeValue(value);
                synchronizedValues.add(existing);
            }
        }

        this.selectableValues.clear();
        this.selectableValues.addAll(synchronizedValues);
    }

    private static String normalizeName(String name) {
        if (name == null) {
            throw new BusinessException(ClothesErrorCode.INVALID_ATTRIBUTE_NAME);
        }
        String normalized = name.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new BusinessException(ClothesErrorCode.INVALID_ATTRIBUTE_NAME);
        }
        return normalized;
    }

    private static List<String> normalizeSelectableValues(List<String> selectableValues) {
        if (selectableValues == null || selectableValues.isEmpty()) {
            throw new BusinessException(ClothesErrorCode.INVALID_SELECTABLE_VALUE);
        }

        Set<String> uniqueValues = new HashSet<>();
        List<String> normalizedValues = new ArrayList<>(selectableValues.size());
        for (String value : selectableValues) {
            if (value == null) {
                throw new BusinessException(ClothesErrorCode.INVALID_SELECTABLE_VALUE);
            }
            String normalized = value.trim();
            if (normalized.isEmpty() || normalized.length() > 100) {
                throw new BusinessException(ClothesErrorCode.INVALID_SELECTABLE_VALUE);
            }
            if (!uniqueValues.add(normalized.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(
                        ClothesErrorCode.DUPLICATE_SELECTABLE_VALUE_IN_REQUEST);
            }
            normalizedValues.add(normalized);
        }
        return normalizedValues;
    }
}
