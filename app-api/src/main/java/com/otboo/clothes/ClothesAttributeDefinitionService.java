package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesAttributeDefCreateRequest;
import com.otboo.clothes.dto.ClothesAttributeDefDto;
import com.otboo.clothes.dto.ClothesAttributeDefUpdateRequest;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.entity.ClothesAttributeSelectableValue;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.clothes.repository.ClothesAttributeSelectableValueRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClothesAttributeDefinitionService {

    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final ClothesAttributeSelectableValueRepository selectableValueRepository;

    private static final String SORT_BY_NAME = "name";
    private static final String SORT_BY_CREATED_AT = "createdAt";

    @Transactional
    public ClothesAttributeDefDto create(ClothesAttributeDefCreateRequest request) {
        String normalizedName = request.name().trim();
        if (definitionRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BusinessException(ClothesErrorCode.DUPLICATE_ATTRIBUTE_DEFINITION_NAME);
        }

        ClothesAttributeDefinition definition = ClothesAttributeDefinition.create(
                request.name(), request.selectableValues());

        try {
            definitionRepository.saveAndFlush(definition);
        } catch (DataIntegrityViolationException exception) {
            // 애플리케이션 확인과 DB UNIQUE 제약 사이의 동시 요청 경쟁도 같은 계약으로 변환한다.
            throw new BusinessException(
                    ClothesErrorCode.DUPLICATE_ATTRIBUTE_DEFINITION_NAME, exception);
        }

        return ClothesAttributeDefDto.from(definition);
    }

    @Transactional(readOnly = true)
    public CursorResponse<ClothesAttributeDefDto> findAll(
            CursorRequest request,
            String keywordLike
    ) {
        CursorRequest normalizedRequest = normalizeRequest(request);
        String normalizedKeyword = normalizeKeyword(keywordLike);
        String sortBy = normalizedRequest.sortBy();
        UUID idAfter = normalizedRequest.idAfter();
        if (normalizedRequest.cursor() != null && idAfter == null) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR)
                    .addDetail("reason", "cursor와 idAfter는 함께 보내야 합니다.");
        }

        List<ClothesAttributeDefinition> definitions = findDefinitions(
                normalizedRequest, normalizedKeyword);
        long totalCount = definitionRepository.countByKeywordLike(normalizedKeyword);
        Map<UUID, List<String>> selectableValuesByDefinitionId = loadSelectableValues(definitions);

        List<ClothesAttributeDefDto> data = definitions.stream()
                .map(definition -> ClothesAttributeDefDto.from(
                        definition,
                        selectableValuesByDefinitionId.getOrDefault(definition.getId(), List.of())))
                .toList();

        Function<ClothesAttributeDefDto, Object> sortKeyExtractor = SORT_BY_NAME.equals(sortBy)
                ? ClothesAttributeDefDto::name
                : ClothesAttributeDefDto::createdAt;

        return CursorResponse.of(
                data,
                normalizedRequest,
                totalCount,
                sortKeyExtractor,
                ClothesAttributeDefDto::id
        );
    }

    @Transactional
    public ClothesAttributeDefDto update(
            UUID definitionId,
            ClothesAttributeDefUpdateRequest request
    ) {
        ClothesAttributeDefinition definition = findDefinition(definitionId);
        if (!request.hasChanges()) {
            throw new BusinessException(ClothesErrorCode.EMPTY_ATTRIBUTE_UPDATE);
        }

        if (request.name() != null) {
            String normalizedName = request.name().trim();
            if (definitionRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, definitionId)) {
                throw new BusinessException(ClothesErrorCode.DUPLICATE_ATTRIBUTE_DEFINITION_NAME);
            }
            definition.changeName(request.name());
            try {
                definitionRepository.flush();
            } catch (DataIntegrityViolationException exception) {
                throw new BusinessException(
                        ClothesErrorCode.DUPLICATE_ATTRIBUTE_DEFINITION_NAME, exception);
            }
        }
        if (request.selectableValues() != null) {
            definition.replaceSelectableValues(request.selectableValues());
            try {
                definitionRepository.flush();
            } catch (DataIntegrityViolationException exception) {
                throw new BusinessException(ClothesErrorCode.SELECTABLE_VALUE_IN_USE, exception);
            }
        }
        return ClothesAttributeDefDto.from(definition);
    }

    @Transactional
    public void delete(UUID definitionId) {
        ClothesAttributeDefinition definition = findDefinition(definitionId);
        try {
            definitionRepository.delete(definition);
            definitionRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ClothesErrorCode.ATTRIBUTE_DEFINITION_IN_USE, exception);
        }
    }

    private ClothesAttributeDefinition findDefinition(UUID definitionId) {
        return definitionRepository.findById(definitionId)
                .orElseThrow(() -> new BusinessException(
                        ClothesErrorCode.ATTRIBUTE_DEFINITION_NOT_FOUND));
    }

    private CursorRequest normalizeRequest(CursorRequest request) {
        String sortBy = request.sortBy();
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = SORT_BY_NAME;
        }
        if (!SORT_BY_NAME.equals(sortBy) && !SORT_BY_CREATED_AT.equals(sortBy)) {
            throw new BusinessException(ClothesErrorCode.INVALID_ATTRIBUTE_DEFINITION_SORT)
                    .addDetail("sortBy", sortBy);
        }

        SortDirection sortDirection = request.sortDirection() == null
                ? SortDirection.ASCENDING
                : request.sortDirection();
        return new CursorRequest(
                request.cursor(), request.idAfter(), request.limit(), sortBy, sortDirection);
    }

    private String normalizeKeyword(String keywordLike) {
        if (keywordLike == null || keywordLike.isBlank()) {
            return null;
        }
        return keywordLike.trim();
    }

    private List<ClothesAttributeDefinition> findDefinitions(
            CursorRequest request,
            String keywordLike
    ) {
        PageRequest pageRequest = PageRequest.of(0, request.fetchSize());
        String sortBy = request.sortBy();
        boolean ascending = request.sortDirection().isAscending();
        if (SORT_BY_NAME.equals(sortBy)) {
            String cursor = CursorCodec.asString(request.cursor());
            return ascending
                    ? definitionRepository.findAfterNameAscending(
                            cursor, request.idAfter(), keywordLike, pageRequest)
                    : definitionRepository.findAfterNameDescending(
                            cursor, request.idAfter(), keywordLike, pageRequest);
        }

        Instant cursor = CursorCodec.asInstant(request.cursor());
        return ascending
                ? definitionRepository.findAfterCreatedAtAscending(
                        cursor, request.idAfter(), keywordLike, pageRequest)
                : definitionRepository.findAfterCreatedAtDescending(
                        cursor, request.idAfter(), keywordLike, pageRequest);
    }

    private Map<UUID, List<String>> loadSelectableValues(
            Collection<ClothesAttributeDefinition> definitions
    ) {
        List<UUID> definitionIds = definitions.stream()
                .map(ClothesAttributeDefinition::getId)
                .toList();
        if (definitionIds.isEmpty()) {
            return Map.of();
        }

        return selectableValueRepository.findAllByDefinitionIds(definitionIds).stream()
                .collect(Collectors.groupingBy(
                        value -> value.getDefinition().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(ClothesAttributeSelectableValue::getValue,
                                Collectors.toList())
                ));
    }
}
