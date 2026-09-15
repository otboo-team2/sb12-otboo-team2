package com.otboo.clothes.extraction;

import java.util.List;

/** Gemini가 반환한 의상 정보 후보다. 저장 전 검증 단계에서 DB 기준으로 다시 확인한다. */
public record GeminiExtractionCandidate(
        String name,
        String type,
        List<AttributeCandidate> attributes,
        List<String> ambiguities
) {

    public GeminiExtractionCandidate {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
    }

    public record AttributeCandidate(
            String definitionId,
            String value,
            String evidence,
            String source,
            boolean optionDependent
    ) {
    }
}
