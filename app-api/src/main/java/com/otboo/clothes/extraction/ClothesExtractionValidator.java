package com.otboo.clothes.extraction;

import com.otboo.clothes.dto.ClothesExtractionDto;
import com.otboo.clothes.dto.ClothesExtractionFailureDto;
import com.otboo.clothes.dto.ClothesExtractionSource;
import com.otboo.clothes.dto.ExtractedClothesAttributeDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Gemini 후보를 현재 DB와 대조해 알맞는 추출 응답으로 바꾼다. */
@Component
public class ClothesExtractionValidator {

    private static final int MAX_EVIDENCE_LENGTH = 200;
    private static final Map<String, List<String>> COLOR_EVIDENCE_TERMS = Map.ofEntries(
            Map.entry("블랙", List.of("블랙", "검정", "black")),
            Map.entry("그레이", List.of("그레이", "회색", "gray", "grey")),
            Map.entry("베이지", List.of("베이지", "beige")),
            Map.entry("브라운", List.of("브라운", "갈색", "brown")),
            Map.entry("블루", List.of("블루", "파랑", "청색", "blue")),
            Map.entry("화이트", List.of("화이트", "흰색", "white")),
            Map.entry("차콜", List.of("차콜", "charcoal"))
    );

    public ClothesExtractionDto validate(
            ProductPageData page,
            GeminiExtractionCandidate candidate,
            List<AttributeDefinitionSnapshot> catalog,
            String validatedImageUrl
    ) {
        List<ClothesExtractionFailureDto> failures = new ArrayList<>();
        Map<UUID, AttributeDefinitionSnapshot> definitions = indexDefinitions(catalog);

        String name = firstNonBlank(
                candidate == null ? null : candidate.name(),
                page == null ? null : page.name());
        ClothesType type = parseType(candidate == null ? null : candidate.type(), failures);

        if (candidate != null) {
            for (String ambiguity : candidate.ambiguities()) {
                if (!isBlank(ambiguity)) {
                    failures.add(new ClothesExtractionFailureDto(
                            "ambiguity", "의상 속성을 하나로 확정할 수 없습니다."));
                }
            }
        }

        List<ValidatedAttribute> validated = new ArrayList<>();
        if (candidate != null) {
            for (GeminiExtractionCandidate.AttributeCandidate attribute : candidate.attributes()) {
                validateAttribute(attribute, definitions, validated, failures);
            }
        }
        removeConflicts(validated, failures);
        removeDuplicateValues(validated);
        removeDuplicateFailures(failures);

        List<ExtractedClothesAttributeDto> attributes = validated.stream()
                .map(ValidatedAttribute::dto)
                .toList();
        String imageUrl = clean(validatedImageUrl);

        if (isBlank(name) && type == null && attributes.isEmpty() && imageUrl == null) {
            throw new BusinessException(ClothesErrorCode.PRODUCT_DATA_NOT_FOUND);
        }

        return new ClothesExtractionDto(name, type, attributes, imageUrl, failures);
    }

    private void validateAttribute(
            GeminiExtractionCandidate.AttributeCandidate candidate,
            Map<UUID, AttributeDefinitionSnapshot> definitions,
            List<ValidatedAttribute> validated,
            List<ClothesExtractionFailureDto> failures
    ) {
        if (candidate == null || candidate.optionDependent()) {
            failures.add(new ClothesExtractionFailureDto(
                    "ambiguity", "옵션에 따라 달라지는 값은 자동 입력하지 않았습니다."));
            return;
        }

        UUID definitionId;
        try {
            definitionId = UUID.fromString(clean(candidate.definitionId()));
        } catch (IllegalArgumentException | NullPointerException exception) {
            failures.add(attributeFailure());
            return;
        }

        AttributeDefinitionSnapshot definition = definitions.get(definitionId);
        if (definition == null) {
            failures.add(attributeFailure());
            return;
        }

        String value = clean(candidate.value());
        String evidence = clean(candidate.evidence());
        if (value == null || evidence == null) {
            failures.add(attributeFailure());
            return;
        }

        String canonicalValue = definition.selectableValues().stream()
                .map(ClothesExtractionValidator::clean)
                .filter(selectable -> selectable != null && selectable.equals(value))
                .findFirst()
                .orElse(null);
        if (canonicalValue == null || !isSupportedSource(candidate.source())) {
            failures.add(attributeFailure());
            return;
        }

        if (isUnsupportedEvidence(definition.name(), canonicalValue, evidence)) {
            failures.add(attributeFailure());
            return;
        }

        ClothesExtractionSource source;
        try {
            source = ClothesExtractionSource.valueOf(clean(candidate.source()));
        } catch (IllegalArgumentException | NullPointerException exception) {
            failures.add(attributeFailure());
            return;
        }
        validated.add(new ValidatedAttribute(
                definitionId,
                definition.name(),
                canonicalValue,
                cap(evidence),
                source));
    }

    private void removeConflicts(
            List<ValidatedAttribute> attributes,
            List<ClothesExtractionFailureDto> failures
    ) {
        Map<UUID, Set<String>> valuesByDefinition = new HashMap<>();
        for (ValidatedAttribute attribute : attributes) {
            valuesByDefinition
                    .computeIfAbsent(attribute.definitionId(), ignored -> new HashSet<>())
                    .add(attribute.value());
        }

        Set<UUID> conflictingDefinitions = valuesByDefinition.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        if (conflictingDefinitions.isEmpty()) {
            return;
        }
        attributes.removeIf(attribute -> conflictingDefinitions.contains(attribute.definitionId()));
        failures.add(new ClothesExtractionFailureDto(
                "attributes", "같은 속성에 서로 다른 값이 있어 자동 입력하지 않았습니다."));
    }

    private boolean isUnsupportedEvidence(
            String definitionName,
            String value,
            String evidence
    ) {
        String normalizedDefinition = clean(definitionName);
        String normalizedEvidence = evidence.toLowerCase(Locale.ROOT);
        if ("두께감".equals(normalizedDefinition)
                && "얇음".equals(value)
                && normalizedEvidence.contains("가벼운 원단")
                && !normalizedEvidence.contains("얇")) {
            return true;
        }
        if ("색상".equals(normalizedDefinition)
                && !matchesColorEvidence(value, normalizedEvidence)) {
            return true;
        }
        return "계절".equals(normalizedDefinition)
                && "여름".equals(value)
                && normalizedEvidence.contains("시원")
                && !normalizedEvidence.contains("여름");
    }

    private boolean matchesColorEvidence(String value, String normalizedEvidence) {
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        return COLOR_EVIDENCE_TERMS.getOrDefault(normalizedValue, List.of(normalizedValue)).stream()
                .anyMatch(normalizedEvidence::contains);
    }

    private void removeDuplicateValues(List<ValidatedAttribute> attributes) {
        Map<String, ValidatedAttribute> unique = new LinkedHashMap<>();
        for (ValidatedAttribute attribute : attributes) {
            unique.putIfAbsent(attribute.definitionId() + "\u0000" + attribute.value(), attribute);
        }
        attributes.clear();
        attributes.addAll(unique.values());
    }

    private void removeDuplicateFailures(List<ClothesExtractionFailureDto> failures) {
        Set<ClothesExtractionFailureDto> unique = new LinkedHashSet<>(failures);
        failures.clear();
        failures.addAll(unique);
    }

    private Map<UUID, AttributeDefinitionSnapshot> indexDefinitions(
            List<AttributeDefinitionSnapshot> catalog
    ) {
        Map<UUID, AttributeDefinitionSnapshot> definitions = new HashMap<>();
        if (catalog == null) {
            return definitions;
        }
        for (AttributeDefinitionSnapshot definition : catalog) {
            if (definition != null && definition.definitionId() != null) {
                definitions.put(definition.definitionId(), definition);
            }
        }
        return definitions;
    }

    private ClothesType parseType(String rawType, List<ClothesExtractionFailureDto> failures) {
        String type = clean(rawType);
        if (type == null) {
            return null;
        }
        try {
            return ClothesType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            failures.add(new ClothesExtractionFailureDto("type", "의상 종류를 확인하지 못했습니다."));
            return null;
        }
    }

    private boolean isSupportedSource(String source) {
        if (clean(source) == null) {
            return false;
        }
        try {
            ClothesExtractionSource.valueOf(clean(source));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ClothesExtractionFailureDto attributeFailure() {
        return new ClothesExtractionFailureDto(
                "attributes", "속성 정의와 선택값 또는 근거를 확인하지 못했습니다.");
    }

    private String cap(String value) {
        return value.length() <= MAX_EVIDENCE_LENGTH
                ? value
                : value.substring(0, MAX_EVIDENCE_LENGTH);
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? clean(first) : clean(second);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ValidatedAttribute(
            UUID definitionId,
            String definitionName,
            String value,
            String evidence,
            ClothesExtractionSource source
    ) {
        private ExtractedClothesAttributeDto dto() {
            return new ExtractedClothesAttributeDto(
                    definitionId, definitionName, value, evidence, source);
        }
    }
}
