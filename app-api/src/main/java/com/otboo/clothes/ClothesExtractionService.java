package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesExtractionDto;
import com.otboo.clothes.dto.ClothesExtractionFailureDto;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
import com.otboo.clothes.entity.ClothesAttributeSelectableValue;
import com.otboo.clothes.extraction.AttributeDefinitionSnapshot;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.extraction.ClothesExtractionMetrics;
import com.otboo.clothes.extraction.ClothesExtractionProperties;
import com.otboo.clothes.extraction.ClothesExtractionValidator;
import com.otboo.clothes.extraction.CImageAnalysisException;
import com.otboo.clothes.extraction.CImageSelectionProperties;
import com.otboo.clothes.extraction.CImageSelectionResult;
import com.otboo.clothes.extraction.CImageSelectionService;
import com.otboo.clothes.extraction.GeminiClothesExtractionClient;
import com.otboo.clothes.extraction.GeminiExtractionCandidate;
import com.otboo.clothes.extraction.ProductPageData;
import com.otboo.clothes.extraction.ProductPageExtractor;
import com.otboo.clothes.extraction.ProductUrlValidator;
import com.otboo.clothes.extraction.RemoteResource;
import com.otboo.clothes.extraction.SafeRemoteResourceClient;
import com.otboo.clothes.repository.ClothesAttributeDefinitionRepository;
import com.otboo.clothes.repository.ClothesAttributeSelectableValueRepository;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.exception.BusinessException;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 상품 링크를 분석하지만 아직 의상을 저장하지 않는 흐름이다. */
@Service
public class ClothesExtractionService {

    private static final String PRIMARY_IMAGE_FIELD = "image";
    private static final String DETAIL_IMAGE_FIELD = "detailImage";
    private static final List<InformationImageKeyword> INFORMATION_IMAGE_KEYWORDS = List.of(
            new InformationImageKeyword("material", 5),
            new InformationImageKeyword("fabric", 5),
            new InformationImageKeyword("composition", 5),
            new InformationImageKeyword("size", 4),
            new InformationImageKeyword("measurement", 4),
            new InformationImageKeyword("measure", 4),
            new InformationImageKeyword("fit", 4),
            new InformationImageKeyword("spec", 4),
            new InformationImageKeyword("care", 3),
            new InformationImageKeyword("washing", 3),
            new InformationImageKeyword("info", 2),
            new InformationImageKeyword("contents", 2));

    private final ProductUrlValidator productUrlValidator;
    private final ProductPageExtractor productPageExtractor;
    private final SafeRemoteResourceClient remoteResourceClient;
    private final GeminiClothesExtractionClient geminiClient;
    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final ClothesAttributeSelectableValueRepository selectableValueRepository;
    private final ClothesExtractionValidator extractionValidator;
    private final ClothesExtractionProperties properties;
    private final ClothesExtractionMetrics metrics;
    private final CImageSelectionProperties cSelectionProperties;
    private final ObjectProvider<CImageSelectionService> cSelectionServiceProvider;

    public ClothesExtractionService(
            ProductUrlValidator productUrlValidator,
            ProductPageExtractor productPageExtractor,
            SafeRemoteResourceClient remoteResourceClient,
            GeminiClothesExtractionClient geminiClient,
            ClothesAttributeDefinitionRepository definitionRepository,
            ClothesAttributeSelectableValueRepository selectableValueRepository,
            ClothesExtractionValidator extractionValidator,
            ClothesExtractionProperties properties,
            ClothesExtractionMetrics metrics,
            CImageSelectionProperties cSelectionProperties,
            ObjectProvider<CImageSelectionService> cSelectionServiceProvider
    ) {
        this.productUrlValidator = productUrlValidator;
        this.productPageExtractor = productPageExtractor;
        this.remoteResourceClient = remoteResourceClient;
        this.geminiClient = geminiClient;
        this.definitionRepository = definitionRepository;
        this.selectableValueRepository = selectableValueRepository;
        this.extractionValidator = extractionValidator;
        this.properties = properties;
        this.metrics = metrics;
        this.cSelectionProperties = cSelectionProperties;
        this.cSelectionServiceProvider = cSelectionServiceProvider;
    }

    public ClothesExtractionDto extract(String rawUrl) {
        CImageSelectionProperties.Mode mode = cSelectionProperties.mode();
        String modeTag = mode == CImageSelectionProperties.Mode.C ? "c" : "b0";
        Timer.Sample sample = metrics.startTimer();
        URI productUrl = null;
        ClothesExtractionMetrics.Outcome outcome = ClothesExtractionMetrics.Outcome.ERROR;
        int acceptedAttributeCount = 0;
        try {
            productUrl = productUrlValidator.validate(rawUrl);
            long collectionStart = System.nanoTime();
            ProductPageData page;
            try {
                page = productPageExtractor.extract(productUrl);
            } catch (RuntimeException | Error exception) {
                metrics.recordStage(
                        productUrl, modeTag, "collection", "error", elapsedSince(collectionStart));
                throw exception;
            }
            metrics.recordStage(
                    productUrl, modeTag, "collection", "success", elapsedSince(collectionStart));
            int discoveredImageCount = page.detailImageUrls().size();
            metrics.recordStageImageCount(
                    productUrl, modeTag, "collection", discoveredImageCount);

            List<ClothesExtractionFailureDto> failures = new ArrayList<>();
            List<RemoteResource> images = new ArrayList<>();
            Set<URI> downloadedUris = new LinkedHashSet<>();
            String validatedImageUrl = downloadPrimaryImage(page, images, downloadedUris, failures);
            if (mode == CImageSelectionProperties.Mode.C) {
                selectCDetails(page, images, downloadedUris, failures);
            } else {
                int failuresBeforeDetails = failures.size();
                long detailsDownloadStart = System.nanoTime();
                DetailDownloadStats details = downloadDetailImages(
                        page, images, downloadedUris, failures);
                metrics.recordStageImageCount(
                        productUrl, modeTag, "download", details.downloadedCount());
                metrics.recordStageImageCount(
                        productUrl, modeTag, "selector", details.selectedCount());
                metrics.recordStageImageCount(productUrl, modeTag, "db18", 0);
                metrics.recordStageImageBytes(
                        productUrl, modeTag, "download", details.downloadedBytes());
                metrics.recordStage(
                        productUrl,
                        modeTag,
                        "download",
                        failures.size() == failuresBeforeDetails ? "success" : "partial",
                        elapsedSince(detailsDownloadStart));
            }

            List<AttributeDefinitionSnapshot> catalog = loadAttributeCatalog();
            int geminiImageCount = images.size();
            long geminiImageBytes = totalImageBytes(images);
            metrics.recordStageImageCount(
                    productUrl, modeTag, "gemini", geminiImageCount);
            metrics.recordStageImageBytes(
                    productUrl, modeTag, "gemini", geminiImageBytes);
            long geminiStart = System.nanoTime();
            GeminiExtractionCandidate candidate;
            try {
                candidate = geminiClient.extract(page, images, catalog);
            } catch (RuntimeException | Error exception) {
                metrics.recordStage(
                        productUrl, modeTag, "gemini", "error", elapsedSince(geminiStart));
                metrics.recordFailureReason(
                        productUrl, modeTag, "gemini", failureReason(exception));
                throw exception;
            }
            metrics.recordStage(
                    productUrl, modeTag, "gemini", "success", elapsedSince(geminiStart));
            ClothesExtractionDto validated = extractionValidator.validate(
                    page, candidate, catalog, validatedImageUrl);

            ClothesExtractionDto result = validated;
            if (!failures.isEmpty()) {
                List<ClothesExtractionFailureDto> mergedFailures = new ArrayList<>(validated.failures());
                mergedFailures.addAll(failures);
                result = new ClothesExtractionDto(
                        validated.name(),
                        validated.type(),
                        validated.attributes(),
                        validated.imageUrl(),
                        mergedFailures);
            }
            acceptedAttributeCount = result.attributes().size();
            outcome = result.failures().isEmpty()
                    ? ClothesExtractionMetrics.Outcome.SUCCESS
                    : ClothesExtractionMetrics.Outcome.PARTIAL;
            return result;
        } finally {
            metrics.recordExtraction(sample, productUrl, outcome, acceptedAttributeCount);
        }
    }

    private void selectCDetails(
            ProductPageData page,
            List<RemoteResource> images,
            Set<URI> downloadedUris,
            List<ClothesExtractionFailureDto> failures
    ) {
        CImageSelectionService selectionService = cSelectionServiceProvider.getIfAvailable();
        if (selectionService == null) {
            throw new BusinessException(ClothesErrorCode.C_IMAGE_ANALYSIS_UNAVAILABLE);
        }

        long remainingBytes = properties.maxTotalImageBytes()
                - images.stream().mapToLong(image -> image.body().length).sum();
        try {
            CImageSelectionResult selection = selectionService.selectDetails(
                    page, Set.copyOf(downloadedUris), remainingBytes);
            images.addAll(selection.images());
            failures.addAll(selection.downloadFailures());
            selection.images().stream()
                    .map(RemoteResource::finalUri)
                    .filter(java.util.Objects::nonNull)
                    .forEach(downloadedUris::add);
        } catch (CImageAnalysisException exception) {
            throw switch (exception.reason()) {
                case TIMEOUT -> new BusinessException(
                        ClothesErrorCode.C_IMAGE_ANALYSIS_TIMEOUT, exception);
                case SCAN_LIMIT -> new BusinessException(
                        ClothesErrorCode.REMOTE_RESOURCE_TOO_LARGE, exception);
                default -> new BusinessException(
                        ClothesErrorCode.C_IMAGE_ANALYSIS_UNAVAILABLE, exception);
            };
        }
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

    private DetailDownloadStats downloadDetailImages(
            ProductPageData page,
            List<RemoteResource> images,
            Set<URI> downloadedUris,
            List<ClothesExtractionFailureDto> failures
    ) {
        int maxDetailImages = Math.max(0, properties.maxDetailImages());
        int totalBytes = images.stream().mapToInt(image -> image.body().length).sum();
        int detailCount = 0;
        long downloadedBytes = 0;
        List<URI> selectedImages = selectDetailImages(page.detailImageUrls(), maxDetailImages);

        for (URI detailUrl : selectedImages) {
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
                downloadedBytes += imageBytes;
                detailCount++;
            } catch (RuntimeException exception) {
                failures.add(imageFailure(DETAIL_IMAGE_FIELD));
            }
        }
        return new DetailDownloadStats(selectedImages.size(), detailCount, downloadedBytes);
    }

    private List<URI> selectDetailImages(List<URI> detailImageUrls, int limit) {
        if (detailImageUrls == null || detailImageUrls.isEmpty() || limit <= 0) {
            return List.of();
        }
        if (detailImageUrls.size() <= limit) {
            return detailImageUrls;
        }

        List<URI> selected = sampleEvenly(detailImageUrls, limit);
        List<URI> informationCandidates = detailImageUrls.stream()
                .filter(image -> informationImageScore(image) > 0)
                .sorted(Comparator
                        .comparingInt(this::informationImageScore)
                        .reversed()
                        .thenComparingInt(detailImageUrls::indexOf))
                .toList();
        for (URI informationCandidate : informationCandidates) {
            if (selected.contains(informationCandidate)) {
                continue;
            }
            int replacementIndex = findReplacementIndex(selected, detailImageUrls);
            if (replacementIndex < 0) {
                break;
            }
            selected.set(replacementIndex, informationCandidate);
        }
        return selected.stream()
                .sorted(Comparator.comparingInt(detailImageUrls::indexOf))
                .toList();
    }

    private List<URI> sampleEvenly(List<URI> detailImageUrls, int limit) {
        if (limit == 1) {
            return List.of(detailImageUrls.get(detailImageUrls.size() - 1));
        }

        List<URI> selected = new ArrayList<>(limit);
        int lastIndex = detailImageUrls.size() - 1;
        for (int index = 0; index < limit; index++) {
            int sourceIndex = (int) Math.round((double) index * lastIndex / (limit - 1));
            selected.add(detailImageUrls.get(sourceIndex));
        }
        return selected;
    }

    private int findReplacementIndex(List<URI> selected, List<URI> allImages) {
        URI first = allImages.get(0);
        URI last = allImages.get(allImages.size() - 1);
        return java.util.stream.IntStream.range(0, selected.size())
                .filter(index -> !selected.get(index).equals(first))
                .filter(index -> !selected.get(index).equals(last))
                .boxed()
                .min(Comparator
                        .comparingInt((Integer index) -> informationImageScore(selected.get(index)))
                        .thenComparingInt(index -> -allImages.indexOf(selected.get(index))))
                .orElse(-1);
    }

    private int informationImageScore(URI image) {
        if (image == null) {
            return 0;
        }
        String value = (image.getPath() + "?" + image.getQuery()).toLowerCase(Locale.ROOT);
        return INFORMATION_IMAGE_KEYWORDS.stream()
                .filter(keyword -> value.contains(keyword.value()))
                .mapToInt(InformationImageKeyword::weight)
                .max()
                .orElse(0);
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

    private long totalImageBytes(List<RemoteResource> images) {
        return images.stream().mapToLong(image -> image.body().length).sum();
    }

    private String failureReason(Throwable exception) {
        if (exception instanceof BusinessException businessException
                && businessException.getErrorCode() == CommonErrorCode.EXTERNAL_API_TIMEOUT) {
            return "timeout";
        }
        return "model_error";
    }

    private Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(Math.max(0, System.nanoTime() - startNanos));
    }

    private record DetailDownloadStats(int selectedCount, int downloadedCount, long downloadedBytes) {
    }

    private record InformationImageKeyword(String value, int weight) {
    }
}
