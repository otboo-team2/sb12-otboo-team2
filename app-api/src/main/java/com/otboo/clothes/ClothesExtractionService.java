package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesExtractionDto;
import com.otboo.clothes.dto.ClothesExtractionFailureDto;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.entity.ClothesAttributeSelectableValue;
import com.otboo.clothes.extraction.AttributeDefinitionSnapshot;
import com.otboo.clothes.extraction.ClothesExtractionProperties;
import com.otboo.clothes.extraction.ClothesExtractionValidator;
import com.otboo.clothes.extraction.GeminiClothesExtractionClient;
import com.otboo.clothes.extraction.GeminiExtractionCandidate;
import com.otboo.clothes.extraction.ProductPageData;
import com.otboo.clothes.extraction.ProductPageExtractor;
import com.otboo.clothes.extraction.ProductUrlValidator;
import com.otboo.clothes.extraction.RemoteResource;
import com.otboo.clothes.extraction.SafeRemoteResourceClient;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.clothes.repository.ClothesAttributeSelectableValueRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** 상품 링크를 분석하지만 아직 의상을 저장하지 않는 흐름이다. */
@Service
public class ClothesExtractionService {

    private static final String PRIMARY_IMAGE_FIELD = "image";
    private static final String DETAIL_IMAGE_FIELD = "detailImage";

    private final ProductUrlValidator productUrlValidator;
    private final ProductPageExtractor productPageExtractor;
    private final SafeRemoteResourceClient remoteResourceClient;
    private final GeminiClothesExtractionClient geminiClient;
    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final ClothesAttributeSelectableValueRepository selectableValueRepository;
    private final ClothesExtractionValidator extractionValidator;
    private final ClothesExtractionProperties properties;

    public ClothesExtractionService(
            ProductUrlValidator productUrlValidator,
            ProductPageExtractor productPageExtractor,
            SafeRemoteResourceClient remoteResourceClient,
            GeminiClothesExtractionClient geminiClient,
            ClothesAttributeDefinitionRepository definitionRepository,
            ClothesAttributeSelectableValueRepository selectableValueRepository,
            ClothesExtractionValidator extractionValidator,
            ClothesExtractionProperties properties
    ) {
        this.productUrlValidator = productUrlValidator;
        this.productPageExtractor = productPageExtractor;
        this.remoteResourceClient = remoteResourceClient;
        this.geminiClient = geminiClient;
        this.definitionRepository = definitionRepository;
        this.selectableValueRepository = selectableValueRepository;
        this.extractionValidator = extractionValidator;
        this.properties = properties;
    }

    public ClothesExtractionDto extract(String rawUrl) {
        URI productUrl = productUrlValidator.validate(rawUrl);
        ProductPageData page = productPageExtractor.extract(productUrl);

        List<ClothesExtractionFailureDto> failures = new ArrayList<>();
        List<RemoteResource> images = new ArrayList<>();
        Set<URI> downloadedUris = new LinkedHashSet<>();
        String validatedImageUrl = downloadPrimaryImage(page, images, downloadedUris, failures);
        downloadDetailImages(page, images, downloadedUris, failures);

        List<AttributeDefinitionSnapshot> catalog = loadAttributeCatalog();
        GeminiExtractionCandidate candidate = geminiClient.extract(page, images, catalog);
        ClothesExtractionDto validated = extractionValidator.validate(
                page, candidate, catalog, validatedImageUrl);

        if (failures.isEmpty()) {
            return validated;
        }
        List<ClothesExtractionFailureDto> mergedFailures = new ArrayList<>(validated.failures());
        mergedFailures.addAll(failures);
        return new ClothesExtractionDto(
                validated.name(),
                validated.type(),
                validated.attributes(),
                validated.imageUrl(),
                mergedFailures);
    }

    private String downloadPrimaryImage(
            ProductPageData page,
            List<RemoteResource> images,
            Set<URI> downloadedUris,
            List<ClothesExtractionFailureDto> failures
    ) {
        if (page.imageUrl() == null) {
            return null;
        }
        try {
            RemoteResource primary = remoteResourceClient.getImage(page.imageUrl());
            if (primary == null || primary.body().length > properties.maxTotalImageBytes()) {
                failures.add(imageFailure(PRIMARY_IMAGE_FIELD));
                return null;
            }
            images.add(primary);
            downloadedUris.add(page.imageUrl());
            downloadedUris.add(primary.finalUri());
            return primary.finalUri() == null ? null : primary.finalUri().toString();
        } catch (RuntimeException exception) {
            failures.add(imageFailure(PRIMARY_IMAGE_FIELD));
            return null;
        }
    }

    private void downloadDetailImages(
            ProductPageData page,
            List<RemoteResource> images,
            Set<URI> downloadedUris,
            List<ClothesExtractionFailureDto> failures
    ) {
        int maxDetailImages = Math.max(0, properties.maxDetailImages());
        int totalBytes = images.stream().mapToInt(image -> image.body().length).sum();
        int detailCount = 0;

        for (URI detailUrl : page.detailImageUrls()) {
            if (detailCount >= maxDetailImages || detailUrl == null || downloadedUris.contains(detailUrl)) {
                continue;
            }
            try {
                RemoteResource detail = remoteResourceClient.getImage(detailUrl);
                if (detail == null || detail.finalUri() == null || downloadedUris.contains(detail.finalUri())) {
                    continue;
                }
                int imageBytes = detail.body().length;
                if ((long) totalBytes + imageBytes > properties.maxTotalImageBytes()) {
                    continue;
                }
                images.add(detail);
                downloadedUris.add(detailUrl);
                downloadedUris.add(detail.finalUri());
                totalBytes += imageBytes;
                detailCount++;
            } catch (RuntimeException exception) {
                failures.add(imageFailure(DETAIL_IMAGE_FIELD));
            }
        }
    }

    private List<AttributeDefinitionSnapshot> loadAttributeCatalog() {
        List<ClothesAttributeDefinition> definitions = definitionRepository.findAll(
                Sort.by(Sort.Direction.ASC, "name"));
        if (definitions.isEmpty()) {
            return List.of();
        }

        List<java.util.UUID> definitionIds = definitions.stream()
                .map(ClothesAttributeDefinition::getId)
                .toList();
        Map<java.util.UUID, List<String>> selectableValues = new HashMap<>();
        for (ClothesAttributeSelectableValue value
                : selectableValueRepository.findAllByDefinitionIds(definitionIds)) {
            if (value.getDefinition() == null || value.getDefinition().getId() == null) {
                continue;
            }
            selectableValues.computeIfAbsent(
                    value.getDefinition().getId(), ignored -> new ArrayList<>())
                    .add(value.getValue());
        }

        return definitions.stream()
                .map(definition -> new AttributeDefinitionSnapshot(
                        definition.getId(),
                        definition.getName(),
                        selectableValues.getOrDefault(definition.getId(), List.of())))
                .toList();
    }

    private ClothesExtractionFailureDto imageFailure(String field) {
        return new ClothesExtractionFailureDto(field, "이미지를 자동으로 가져오지 못했습니다.");
    }
}
