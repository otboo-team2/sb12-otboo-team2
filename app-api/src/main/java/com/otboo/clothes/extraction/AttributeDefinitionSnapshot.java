package com.otboo.clothes.extraction;

import java.util.List;
import java.util.UUID;

/** Gemini에게 전달할 현재 의상 속성 정의의 읽기 전용 스냅샷이다. */
public record AttributeDefinitionSnapshot(
        UUID definitionId,
        String name,
        List<String> selectableValues
) {

    public AttributeDefinitionSnapshot {
        selectableValues = selectableValues == null
                ? List.of()
                : List.copyOf(selectableValues);
    }
}
