package com.otboo.user.preference;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record UserPreferenceUpdateRequest(
        @NotNull(message = "선호 선택값을 입력해주세요.")
        List<@NotNull UUID> selectableValueIds
) {
}
