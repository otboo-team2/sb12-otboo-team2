package com.otboo.clothes.entity;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.entity.BaseEntity;
import com.otboo.common.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "clothes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Clothes extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "owner_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ClothesType type;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @OneToMany(
            mappedBy = "clothes",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("createdAt ASC, id ASC")
    private final List<ClothesAttributeValue> attributes = new ArrayList<>();

    private Clothes(UUID ownerId, String name, ClothesType type, String imageUrl) {
        this.ownerId = ownerId;
        this.name = name;
        this.type = type;
        this.imageUrl = imageUrl;
        this.favorite = false;
    }

    public static Clothes create(
            UUID ownerId,
            String name,
            ClothesType type,
            String imageUrl
    ) {
        return new Clothes(ownerId, name, type, imageUrl);
    }

    public List<ClothesAttributeValue> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    /**
     * 의상에 속성값을 추가한다. 같은 정의를 두 번 담지 않는 규칙은 애플리케이션과 DB가 함께 보호한다.
     */
    public void addAttribute(UUID definitionId, UUID selectableValueId) {
        boolean alreadyAdded = attributes.stream()
                .anyMatch(attribute -> Objects.equals(attribute.getDefinitionId(), definitionId));
        if (alreadyAdded) {
            throw new BusinessException(ClothesErrorCode.DUPLICATE_CLOTHES_ATTRIBUTE);
        }
        attributes.add(ClothesAttributeValue.create(this, definitionId, selectableValueId));
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeType(ClothesType type) {
        this.type = type;
    }

    public void changeImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    /** 기존 속성 행은 가능한 한 재사용하고, 추가·변경·삭제만 반영한다. */
    public void replaceAttributes(Map<UUID, UUID> selectableValueIdsByDefinitionId) {
        Set<UUID> definitionIds = selectableValueIdsByDefinitionId.keySet();
        attributes.removeIf(attribute -> !definitionIds.contains(attribute.getDefinitionId()));

        selectableValueIdsByDefinitionId.forEach((definitionId, selectableValueId) -> {
            ClothesAttributeValue existing = attributes.stream()
                    .filter(attribute -> attribute.getDefinitionId().equals(definitionId))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                attributes.add(ClothesAttributeValue.create(
                        this, definitionId, selectableValueId));
            } else {
                existing.changeSelectableValueId(selectableValueId);
            }
        });
    }

    public void changeFavorite(boolean favorite) {
        this.favorite = favorite;
    }
}
