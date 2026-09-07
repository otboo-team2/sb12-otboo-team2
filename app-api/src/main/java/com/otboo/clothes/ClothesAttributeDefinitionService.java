package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesAttributeDefCreateRequest;
import com.otboo.clothes.dto.ClothesAttributeDefDto;
import com.otboo.clothes.dto.ClothesAttributeDefUpdateRequest;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.common.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClothesAttributeDefinitionService {

    private final ClothesAttributeDefinitionRepository definitionRepository;

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
}
