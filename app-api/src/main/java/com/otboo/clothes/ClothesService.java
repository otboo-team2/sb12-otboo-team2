package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesAttributeDto;
import com.otboo.clothes.dto.ClothesAttributeWithDefDto;
import com.otboo.clothes.dto.ClothesCreateRequest;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.dto.ClothesUpdateRequest;
import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.entity.ClothesAttributeSelectableValue;
import com.otboo.clothes.entity.ClothesAttributeValue;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.clothes.repository.ClothesAttributeSelectableValueRepository;
import com.otboo.clothes.repository.ClothesAttributeValueRepository;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClothesService {

    private final ClothesRepository clothesRepository;
    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final ClothesAttributeSelectableValueRepository selectableValueRepository;
    private final ClothesAttributeValueRepository attributeValueRepository;

    @Transactional
    public ClothesDto create(UUID authenticatedUserId, ClothesCreateRequest request) {
        if (!authenticatedUserId.equals(request.ownerId())) {
            throw new BusinessException(ClothesErrorCode.NOT_OWNER);
        }

        List<ClothesAttributeDto> requestedAttributes = request.attributes();
        validateNoDuplicateDefinitions(requestedAttributes);

        List<UUID> definitionIds = requestedAttributes.stream()
                .map(ClothesAttributeDto::definitionId)
                .distinct()
                .toList();
        Map<UUID, ClothesAttributeDefinition> definitions = loadDefinitions(definitionIds);
        Map<UUID, List<ClothesAttributeSelectableValue>> selectableValuesByDefinitionId =
                loadSelectableValues(definitionIds);

        Clothes clothes = Clothes.create(
                authenticatedUserId,
                request.name(),
                request.type(),
                null);
        requestedAttributes.forEach(attribute -> addAttribute(
                clothes,
                attribute,
                definitions,
                selectableValuesByDefinitionId));

        Clothes saved = clothesRepository.saveAndFlush(clothes);
        return toDto(saved, saved.getAttributes(), definitions, selectableValuesByDefinitionId);
    }

    @Transactional(readOnly = true)
    public CursorResponse<ClothesDto> findAll(
            UUID ownerId,
            ClothesType typeEqual,
            CursorRequest request
    ) {
        CursorRequest normalizedRequest = normalizeListRequest(request);
        UUID cursorId = CursorCodec.asUuid(normalizedRequest.cursor());
        long totalCount = clothesRepository.countByOwnerIdAndType(ownerId, typeEqual);
        List<Clothes> clothes = clothesRepository.findAfterIdDescending(
                ownerId,
                typeEqual,
                cursorId,
                PageRequest.of(0, normalizedRequest.fetchSize()));

        List<ClothesDto> data = toDtos(clothes);
        return CursorResponse.of(
                data,
                normalizedRequest,
                totalCount,
                ClothesDto::id,
                ClothesDto::id);
    }

    @Transactional
    public ClothesDto update(
            UUID authenticatedUserId,
            UUID clothesId,
            ClothesUpdateRequest request
    ) {
        Clothes clothes = clothesRepository.findById(clothesId)
                .orElseThrow(() -> new BusinessException(ClothesErrorCode.CLOTHES_NOT_FOUND));
        if (!authenticatedUserId.equals(clothes.getOwnerId())) {
            throw new BusinessException(ClothesErrorCode.NOT_OWNER);
        }
        if (!request.hasChanges()) {
            throw new BusinessException(ClothesErrorCode.EMPTY_CLOTHES_UPDATE);
        }

        if (request.attributes() != null) {
            Map<UUID, UUID> selectableValueIdsByDefinitionId =
                    validateAndResolveAttributes(request.attributes());
            clothes.replaceAttributes(selectableValueIdsByDefinitionId);
        }
        if (request.name() != null) {
            clothes.changeName(request.name());
        }
        if (request.type() != null) {
            clothes.changeType(request.type());
        }

        List<ClothesAttributeValue> attributes = clothes.getAttributes();
        AttributeMetadata metadata = loadAttributeMetadata(attributes);
        return toDto(clothes, attributes, metadata.definitions(), metadata.selectableValues());
    }

    private CursorRequest normalizeListRequest(CursorRequest request) {
        String cursor = request.cursor();
        UUID idAfter = request.idAfter();
        if ((cursor == null) != (idAfter == null)) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR)
                    .addDetail("reason", "cursor와 idAfter는 함께 보내야 합니다.");
        }
        if (cursor != null && !CursorCodec.asUuid(cursor).equals(idAfter)) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR)
                    .addDetail("reason", "cursor와 idAfter가 일치하지 않습니다.");
        }
        return new CursorRequest(cursor, idAfter, request.limit(), "id", SortDirection.DESCENDING);
    }

    private List<ClothesDto> toDtos(List<Clothes> clothes) {
        if (clothes.isEmpty()) {
            return List.of();
        }

        List<UUID> clothesIds = clothes.stream()
                .map(Clothes::getId)
                .toList();
        List<ClothesAttributeValue> attributeValues =
                attributeValueRepository.findAllByClothesIdIn(clothesIds);
        Map<UUID, List<ClothesAttributeValue>> attributesByClothesId = attributeValues.stream()
                .collect(Collectors.groupingBy(
                        value -> value.getClothes().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<UUID> definitionIds = attributeValues.stream()
                .map(ClothesAttributeValue::getDefinitionId)
                .distinct()
                .toList();
        Map<UUID, ClothesAttributeDefinition> definitions = loadDefinitions(definitionIds);
        Map<UUID, List<ClothesAttributeSelectableValue>> selectableValuesByDefinitionId =
                loadSelectableValues(definitionIds);

        return clothes.stream()
                .map(clothesEntity -> toDto(
                        clothesEntity,
                        attributesByClothesId.getOrDefault(clothesEntity.getId(), List.of()),
                        definitions,
                        selectableValuesByDefinitionId))
                .toList();
    }

    private Map<UUID, UUID> validateAndResolveAttributes(List<ClothesAttributeDto> attributes) {
        Set<UUID> definitionIds = new LinkedHashSet<>();
        for (ClothesAttributeDto attribute : attributes) {
            if (!definitionIds.add(attribute.definitionId())) {
                throw new BusinessException(ClothesErrorCode.DUPLICATE_CLOTHES_ATTRIBUTE);
            }
        }

        loadDefinitions(definitionIds.stream().toList());
        Map<UUID, List<ClothesAttributeSelectableValue>> selectableValuesByDefinitionId =
                loadSelectableValues(definitionIds.stream().toList());
        Map<UUID, UUID> selectedValueIdsByDefinitionId = new LinkedHashMap<>();
        for (ClothesAttributeDto attribute : attributes) {
            ClothesAttributeSelectableValue selectableValue = selectableValuesByDefinitionId
                    .getOrDefault(attribute.definitionId(), List.of()).stream()
                    .filter(value -> value.getValue().equals(attribute.value()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            ClothesErrorCode.INVALID_SELECTABLE_VALUE));
            selectedValueIdsByDefinitionId.put(
                    attribute.definitionId(), selectableValue.getId());
        }

        return selectedValueIdsByDefinitionId;
    }

    private AttributeMetadata loadAttributeMetadata(Collection<ClothesAttributeValue> attributes) {
        List<UUID> definitionIds = attributes.stream()
                .map(ClothesAttributeValue::getDefinitionId)
                .distinct()
                .toList();
        return new AttributeMetadata(
                loadDefinitions(definitionIds),
                loadSelectableValues(definitionIds));
    }

    private void validateNoDuplicateDefinitions(List<ClothesAttributeDto> attributes) {
        Set<UUID> definitionIds = new HashSet<>();
        for (ClothesAttributeDto attribute : attributes) {
            if (!definitionIds.add(attribute.definitionId())) {
                throw new BusinessException(ClothesErrorCode.DUPLICATE_CLOTHES_ATTRIBUTE);
            }
        }
    }

    private record AttributeMetadata(
            Map<UUID, ClothesAttributeDefinition> definitions,
            Map<UUID, List<ClothesAttributeSelectableValue>> selectableValues
    ) {
    }

    private Map<UUID, ClothesAttributeDefinition> loadDefinitions(List<UUID> definitionIds) {
        if (definitionIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ClothesAttributeDefinition> definitions = definitionRepository
                .findAllById(definitionIds).stream()
                .collect(Collectors.toMap(
                        ClothesAttributeDefinition::getId,
                        Function.identity()));
        if (definitions.size() != definitionIds.size()) {
            throw new BusinessException(ClothesErrorCode.ATTRIBUTE_DEFINITION_NOT_FOUND);
        }
        return definitions;
    }

    private Map<UUID, List<ClothesAttributeSelectableValue>> loadSelectableValues(
            Collection<UUID> definitionIds
    ) {
        if (definitionIds.isEmpty()) {
            return Map.of();
        }

        return selectableValueRepository.findAllByDefinitionIds(definitionIds).stream()
                .collect(Collectors.groupingBy(
                        value -> value.getDefinition().getId(),
                        HashMap::new,
                        Collectors.toList()));
    }

    private void addAttribute(
            Clothes clothes,
            ClothesAttributeDto request,
            Map<UUID, ClothesAttributeDefinition> definitions,
            Map<UUID, List<ClothesAttributeSelectableValue>> selectableValuesByDefinitionId
    ) {
        ClothesAttributeDefinition definition = definitions.get(request.definitionId());
        List<ClothesAttributeSelectableValue> selectableValues =
                selectableValuesByDefinitionId.getOrDefault(request.definitionId(), List.of());
        ClothesAttributeSelectableValue selectableValue = selectableValues.stream()
                .filter(value -> value.getValue().equals(request.value()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ClothesErrorCode.INVALID_SELECTABLE_VALUE));

        clothes.addAttribute(definition.getId(), selectableValue.getId());
    }

    private ClothesDto toDto(
            Clothes clothes,
            Collection<ClothesAttributeValue> attributes,
            Map<UUID, ClothesAttributeDefinition> definitions,
            Map<UUID, List<ClothesAttributeSelectableValue>> selectableValuesByDefinitionId
    ) {
        List<ClothesAttributeWithDefDto> attributeDtos = attributes.stream()
                .map(attribute -> {
                    ClothesAttributeDefinition definition = definitions.get(attribute.getDefinitionId());
                    List<ClothesAttributeSelectableValue> selectableValues =
                            selectableValuesByDefinitionId.getOrDefault(
                                    attribute.getDefinitionId(), List.of());
                    String selectedValue = selectableValues.stream()
                            .filter(value -> value.getId().equals(attribute.getSelectableValueId()))
                            .map(ClothesAttributeSelectableValue::getValue)
                            .findFirst()
                            .orElseThrow(() -> new BusinessException(
                                    ClothesErrorCode.INVALID_SELECTABLE_VALUE));
                    return new ClothesAttributeWithDefDto(
                            definition.getId(),
                            definition.getName(),
                            selectableValues.stream()
                                    .map(ClothesAttributeSelectableValue::getValue)
                                    .toList(),
                            selectedValue);
                })
                .toList();

        return new ClothesDto(
                clothes.getId(),
                clothes.getOwnerId(),
                clothes.getName(),
                clothes.getImageUrl(),
                clothes.getType(),
                attributeDtos);
    }
}
