package com.otboo.user.preference;

import com.otboo.clothes.entity.ClothesAttributeSelectableValue;
import com.otboo.common.entity.BaseEntity;
import com.otboo.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 선택한 의상 속성값. 현재는 스타일 선호를 저장한다. */
@Entity
@Getter
@Table(name = "user_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selectable_value_id", nullable = false)
    private ClothesAttributeSelectableValue selectableValue;

    private UserPreference(User user, ClothesAttributeSelectableValue selectableValue) {
        this.user = user;
        this.selectableValue = selectableValue;
    }

    public static UserPreference create(User user, ClothesAttributeSelectableValue selectableValue) {
        return new UserPreference(user, selectableValue);
    }
}
