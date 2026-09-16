package com.otboo.user.preference;

import java.util.UUID;

public record UserPreferenceDto(
        UUID selectableValueId,
        String definitionName,
        String value
) {

    public static UserPreferenceDto from(UserPreference preference) {
        var selectableValue = preference.getSelectableValue();
        return new UserPreferenceDto(
                selectableValue.getId(),
                selectableValue.getDefinition().getName(),
                selectableValue.getValue());
    }
}
