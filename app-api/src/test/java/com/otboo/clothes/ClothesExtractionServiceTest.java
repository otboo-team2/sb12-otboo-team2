package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.clothes.dto.ClothesExtractionDto;
import com.otboo.clothes.dto.ClothesExtractionFailureDto;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

class ClothesExtractionServiceTest {

    private static final String RAW_URL = "https://shop.example.com/products/1";
    private static final URI PRODUCT_URL = URI.create(RAW_URL);
    private static final URI PRIMARY_URL = URI.create("https://cdn.example.com/main.jpg");
    private static final UUID DEFINITION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock ProductUrlValidator productUrlValidator;
    @Mock ProductPageExtractor productPageExtractor;
    @Mock SafeRemoteResourceClient remoteResourceClient;
    @Mock GeminiClothesExtractionClient geminiClient;
    @Mock ClothesAttributeDefinitionRepository definitionRepository;
    @Mock ClothesAttributeSelectableValueRepository selectableValueRepository;
    @Mock ClothesExtractionValidator extractionValidator;
    @Mock CImageSelectionService cImageSelectionService;
    @Mock ObjectProvider<CImageSelectionService> cImageSelectionServiceProvider;

    private ClothesExtractionProperties properties;
    private CImageSelectionProperties cProperties;
    private SimpleMeterRegistry meterRegistry;
    private ClothesExtractionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new ClothesExtractionProperties(
                "test-key", "gemini-test", 3, 2 * 1024 * 1024,
                10 * 1024 * 1024, 25 * 1024 * 1024, 4, 15_000, 200);
        cProperties = new CImageSelectionProperties(
                CImageSelectionProperties.Mode.B0,
                null,
                "",
                null,
                8,
                100 * 1024 * 1024,
                200_000_000,
                1,
                Duration.ofMillis(250),
                Duration.ofSeconds(30));
        meterRegistry = new SimpleMeterRegistry();
        service = createService(cProperties);
    }

    @Test
    void cModeSendsOnlyFilteredOriginalImagesAndCallsGeminiOnce() {
        service = createService(withMode(CImageSelectionProperties.Mode.C));
        ProductPageData page = pageWithDetails(8);
        List<RemoteResource> selectedImages = List.of(
                resource(URI.create("https://cdn.example.com/detail-2.jpg")),
                resource(URI.create("https://cdn.example.com/detail-8.jpg")));
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(page);
        given(remoteResourceClient.getImage(PRIMARY_URL)).willReturn(resource(PRIMARY_URL));
        given(cImageSelectionServiceProvider.getIfAvailable()).willReturn(cImageSelectionService);
        given(cImageSelectionService.selectDetails(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .willReturn(new CImageSelectionResult(
                        List.of(1, 7), selectedImages, List.of(), 8, 8, 0, 16,
                        Duration.ZERO, Duration.ZERO));
        given(definitionRepository.findAll(any(Sort.class))).willReturn(List.of());
        given(geminiClient.extract(any(), anyList(), anyList()))
                .willReturn(new GeminiExtractionCandidate("상품", "TOP", List.of(), List.of()));
        ClothesExtractionDto expected = new ClothesExtractionDto(
                "상품", null, List.of(), PRIMARY_URL.toString(), List.of());
        given(extractionValidator.validate(any(), any(), anyList(), anyString())).willReturn(expected);

        ClothesExtractionDto result = service.extract(RAW_URL);

        assertThat(result).isSameAs(expected);
        assertStageRecorded("collection", "c", "success");
        assertStageRecorded("gemini", "c", "success");
        assertThat(stageImageCount("c", "collection")).isEqualTo(8);
        assertThat(stageImageCount("c", "gemini")).isEqualTo(3);
        verify(cImageSelectionServiceProvider).getIfAvailable();
        verify(cImageSelectionService).selectDetails(
                any(), any(), org.mockito.ArgumentMatchers.eq(25L * 1024 * 1024 - 1));
        verify(remoteResourceClient).getImage(PRIMARY_URL);
        verify(remoteResourceClient, never()).getImage(URI.create("https://cdn.example.com/detail-1.jpg"));
        verify(geminiClient).extract(any(), org.mockito.ArgumentMatchers.argThat(images ->
                images.size() == 3
                        && images.stream().map(RemoteResource::finalUri).toList().equals(List.of(
                                PRIMARY_URL,
                                URI.create("https://cdn.example.com/detail-2.jpg"),
                                URI.create("https://cdn.example.com/detail-8.jpg")))), anyList());
    }

    @Test
    void cModeDoesNotRunB0OrGeminiWhenAnalysisFails() {
        service = createService(withMode(CImageSelectionProperties.Mode.C));
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(pageWithDetails(8));
        given(remoteResourceClient.getImage(PRIMARY_URL)).willReturn(resource(PRIMARY_URL));
        given(cImageSelectionServiceProvider.getIfAvailable()).willReturn(cImageSelectionService);
        given(cImageSelectionService.selectDetails(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .willThrow(new CImageAnalysisException(CImageAnalysisException.Reason.MODEL_ERROR));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extract(RAW_URL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ClothesErrorCode.C_IMAGE_ANALYSIS_UNAVAILABLE));

        verify(remoteResourceClient, never()).getImage(URI.create("https://cdn.example.com/detail-1.jpg"));
        verify(geminiClient, never()).extract(any(), anyList(), anyList());
        verify(definitionRepository, never()).findAll(any(Sort.class));
        assertThat(meterRegistry.get("otboo_clothes_extraction")
                .tag("shop", "other")
                .tag("outcome", "error")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void aNewB0ConfiguredServiceCanProcessAfterCAnalysisFailure() {
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(pageWithDetails(8));
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(cImageSelectionServiceProvider.getIfAvailable()).willReturn(cImageSelectionService);
        given(cImageSelectionService.selectDetails(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .willThrow(new CImageAnalysisException(CImageAnalysisException.Reason.MODEL_ERROR));
        given(definitionRepository.findAll(any(Sort.class))).willReturn(List.of());
        given(geminiClient.extract(any(), anyList(), anyList()))
                .willReturn(new GeminiExtractionCandidate("상품", "TOP", List.of(), List.of()));
        ClothesExtractionDto expected = new ClothesExtractionDto(
                "상품", null, List.of(), PRIMARY_URL.toString(), List.of());
        given(extractionValidator.validate(any(), any(), anyList(), anyString())).willReturn(expected);

        ClothesExtractionService cModeService = createService(
                withMode(CImageSelectionProperties.Mode.C));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cModeService.extract(RAW_URL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.C_IMAGE_ANALYSIS_UNAVAILABLE));
        verify(geminiClient, never()).extract(any(), anyList(), anyList());

        // Model an application restart with the approved B0 mode: a new service instance
        // routes the next request through the existing six-detail-image path.
        properties = new ClothesExtractionProperties(
                "test-key", "gemini-test", 3, 2 * 1024 * 1024,
                10 * 1024 * 1024, 25 * 1024 * 1024, 6, 15_000, 200);
        ClothesExtractionService b0ModeService = createService(
                withMode(CImageSelectionProperties.Mode.B0));
        ClothesExtractionDto result = b0ModeService.extract(RAW_URL);

        assertThat(result).isSameAs(expected);
        verify(cImageSelectionServiceProvider, times(1)).getIfAvailable();
        verify(cImageSelectionService, times(1)).selectDetails(any(), any(),
                org.mockito.ArgumentMatchers.anyLong());
        verify(geminiClient).extract(any(), org.mockito.ArgumentMatchers.argThat(images ->
                images.stream().map(RemoteResource::finalUri).collect(java.util.stream.Collectors.toSet())
                        .equals(java.util.Set.of(
                                PRIMARY_URL,
                                URI.create("https://cdn.example.com/detail-1.jpg"),
                                URI.create("https://cdn.example.com/detail-2.jpg"),
                                URI.create("https://cdn.example.com/detail-4.jpg"),
                                URI.create("https://cdn.example.com/detail-5.jpg"),
                                URI.create("https://cdn.example.com/detail-7.jpg"),
                                URI.create("https://cdn.example.com/detail-8.jpg")))), anyList());
    }

    @Test
    void cModeMapsAnalysisTimeoutToGatewayTimeoutWithoutRetryingGemini() {
        service = createService(withMode(CImageSelectionProperties.Mode.C));
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(pageWithDetails(1));
        given(remoteResourceClient.getImage(PRIMARY_URL)).willReturn(resource(PRIMARY_URL));
        given(cImageSelectionServiceProvider.getIfAvailable()).willReturn(cImageSelectionService);
        given(cImageSelectionService.selectDetails(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .willThrow(new CImageAnalysisException(CImageAnalysisException.Reason.TIMEOUT));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extract(RAW_URL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ClothesErrorCode.C_IMAGE_ANALYSIS_TIMEOUT));
        verify(geminiClient, never()).extract(any(), anyList(), anyList());
    }

    @Test
    void cModeMapsScanLimitToPayloadTooLargeWithoutRetryingGemini() {
        service = createService(withMode(CImageSelectionProperties.Mode.C));
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(pageWithDetails(1));
        given(remoteResourceClient.getImage(PRIMARY_URL)).willReturn(resource(PRIMARY_URL));
        given(cImageSelectionServiceProvider.getIfAvailable()).willReturn(cImageSelectionService);
        given(cImageSelectionService.selectDetails(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .willThrow(new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extract(RAW_URL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ClothesErrorCode.REMOTE_RESOURCE_TOO_LARGE));
        verify(geminiClient, never()).extract(any(), anyList(), anyList());
    }

    @Test
    void cModeWithoutSelectionServiceFailsAsUnavailable() {
        service = createService(withMode(CImageSelectionProperties.Mode.C));
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(pageWithDetails(1));
        given(cImageSelectionServiceProvider.getIfAvailable()).willReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extract(RAW_URL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ClothesErrorCode.C_IMAGE_ANALYSIS_UNAVAILABLE));
        verify(geminiClient, never()).extract(any(), anyList(), anyList());
    }

    private ClothesExtractionService createService(CImageSelectionProperties selectionProperties) {
        return new ClothesExtractionService(
                productUrlValidator,
                productPageExtractor,
                remoteResourceClient,
                geminiClient,
                definitionRepository,
                selectableValueRepository,
                extractionValidator,
                properties,
                new ClothesExtractionMetrics(meterRegistry),
                selectionProperties,
                cImageSelectionServiceProvider);
    }

    private CImageSelectionProperties withMode(CImageSelectionProperties.Mode mode) {
        return new CImageSelectionProperties(
                mode,
                cProperties.modelPath(),
                cProperties.modelSha256(),
                cProperties.tempDirectory(),
                cProperties.maxSelectedImages(),
                cProperties.maxScanBytes(),
                cProperties.maxDecodedPixels(),
                cProperties.maxConcurrentAnalyses(),
                cProperties.acquireTimeout(),
                cProperties.analysisTimeout());
    }

    private void assertStageRecorded(String stage, String mode, String outcome) {
        assertThat(meterRegistry.get("otboo_clothes_extraction_stage")
                .tag("shop", "other")
                .tag("mode", mode)
                .tag("stage", stage)
                .tag("outcome", outcome)
                .timer()
                .count()).isEqualTo(1);
    }

    private double stageImageCount(String mode, String stage) {
        return meterRegistry.get("otboo_clothes_extraction_stage_images")
                .tag("shop", "other")
                .tag("mode", mode)
                .tag("stage", stage)
                .summary()
                .totalAmount();
    }

    private double stageImageBytes(String mode, String stage) {
        return meterRegistry.get("otboo_clothes_extraction_stage_bytes")
                .tag("shop", "other")
                .tag("mode", mode)
                .tag("stage", stage)
                .summary()
                .totalAmount();
    }

    @Test
    void validatesDownloadsCatalogsCallsGeminiOnceAndDoesNotPersist() {
        ProductPageData page = pageWithDetails(5);
        ClothesAttributeDefinition definition = mockDefinition();
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(page);
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(definitionRepository.findAll(any(Sort.class))).willReturn(List.of(definition));
        given(selectableValueRepository.findAllByDefinitionIds(anyList())).willReturn(List.of());
        given(geminiClient.extract(any(), anyList(), anyList()))
                .willReturn(new GeminiExtractionCandidate("상품", "TOP", List.of(), List.of()));
        ClothesExtractionDto expected = new ClothesExtractionDto(
                "상품", null, List.of(), PRIMARY_URL.toString(), List.of());
        given(extractionValidator.validate(any(), any(), anyList(), anyString())).willReturn(expected);

        ClothesExtractionDto result = service.extract(RAW_URL);

        assertThat(result).isSameAs(expected);
        assertThat(meterRegistry.get("otboo_clothes_extraction")
                .tag("shop", "other")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
        assertStageRecorded("collection", "b0", "success");
        assertStageRecorded("download", "b0", "success");
        assertStageRecorded("gemini", "b0", "success");
        assertThat(stageImageCount("b0", "collection")).isEqualTo(5);
        assertThat(stageImageCount("b0", "download")).isEqualTo(4);
        assertThat(stageImageCount("b0", "selector")).isEqualTo(4);
        assertThat(stageImageCount("b0", "db18")).isZero();
        assertThat(stageImageCount("b0", "gemini")).isEqualTo(5);
        assertThat(stageImageBytes("b0", "download")).isPositive();
        assertThat(stageImageBytes("b0", "gemini")).isPositive();
        InOrder order = inOrder(
                productUrlValidator,
                productPageExtractor,
                remoteResourceClient,
                definitionRepository,
                selectableValueRepository,
                geminiClient,
                extractionValidator);
        order.verify(productUrlValidator).validate(RAW_URL);
        order.verify(productPageExtractor).extract(PRODUCT_URL);
        order.verify(remoteResourceClient).getImage(PRIMARY_URL);
        order.verify(definitionRepository).findAll(any(Sort.class));
        order.verify(selectableValueRepository).findAllByDefinitionIds(anyList());
        order.verify(geminiClient).extract(any(), anyList(), anyList());
        order.verify(extractionValidator).validate(any(), any(), anyList(), anyString());
        verify(remoteResourceClient, org.mockito.Mockito.times(5)).getImage(any(URI.class));
        verify(geminiClient).extract(any(), org.mockito.ArgumentMatchers.argThat(images -> images.size() == 5), anyList());
        verify(cImageSelectionServiceProvider, never()).getIfAvailable();
    }

    @Test
    void continuesAfterIndividualDetailImageFailure() {
        URI detail1 = URI.create("https://cdn.example.com/detail-1.jpg");
        URI detail2 = URI.create("https://cdn.example.com/detail-2.jpg");
        URI detail3 = URI.create("https://cdn.example.com/detail-3.jpg");
        ProductPageData page = new ProductPageData(
                PRODUCT_URL, "상품", "설명", PRIMARY_URL, List.of(detail1, detail2, detail3), List.of());
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(page);
        given(remoteResourceClient.getImage(PRIMARY_URL)).willReturn(resource(PRIMARY_URL));
        given(remoteResourceClient.getImage(detail1)).willReturn(resource(detail1));
        given(remoteResourceClient.getImage(detail2))
                .willThrow(new BusinessException(com.otboo.clothes.exception.ClothesErrorCode.INVALID_REMOTE_IMAGE));
        given(remoteResourceClient.getImage(detail3)).willReturn(resource(detail3));
        given(definitionRepository.findAll(any(Sort.class))).willReturn(List.of());
        given(geminiClient.extract(any(), anyList(), anyList()))
                .willReturn(new GeminiExtractionCandidate("상품", "TOP", List.of(), List.of()));
        ClothesExtractionDto expected = new ClothesExtractionDto(
                "상품", null, List.of(), PRIMARY_URL.toString(), List.of());
        given(extractionValidator.validate(any(), any(), anyList(), anyString())).willReturn(expected);

        ClothesExtractionDto result = service.extract(RAW_URL);

        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure.field()).isEqualTo("detailImage"));
        assertThat(meterRegistry.get("otboo_clothes_extraction")
                .tag("shop", "other")
                .tag("outcome", "partial")
                .timer()
                .count()).isEqualTo(1);
        verify(remoteResourceClient).getImage(detail3);
        verify(geminiClient).extract(any(), org.mockito.ArgumentMatchers.argThat(images -> images.size() == 3), anyList());
    }

    @Test
    void recordsGeminiTimeoutAsFixedTimeoutReason() {
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(pageWithDetails(0));
        given(remoteResourceClient.getImage(PRIMARY_URL)).willReturn(resource(PRIMARY_URL));
        given(definitionRepository.findAll(any(Sort.class))).willReturn(List.of());
        given(geminiClient.extract(any(), anyList(), anyList()))
                .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_API_TIMEOUT));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extract(RAW_URL))
                .isInstanceOf(BusinessException.class);

        assertThat(meterRegistry.get("otboo_clothes_extraction_stage")
                .tag("shop", "other")
                .tag("mode", "b0")
                .tag("stage", "gemini")
                .tag("outcome", "error")
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("otboo_clothes_extraction_stage_failures")
                .tag("shop", "other")
                .tag("mode", "b0")
                .tag("stage", "gemini")
                .tag("reason", "timeout")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void samplesDetailImagesAcrossTheWholePage() {
        ProductPageData page = pageWithDetails(8);
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(page);
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(definitionRepository.findAll(any(Sort.class))).willReturn(List.of());
        given(geminiClient.extract(any(), anyList(), anyList()))
                .willReturn(new GeminiExtractionCandidate("상품", "TOP", List.of(), List.of()));
        ClothesExtractionDto expected = new ClothesExtractionDto(
                "상품", null, List.of(), PRIMARY_URL.toString(), List.of());
        given(extractionValidator.validate(any(), any(), anyList(), anyString())).willReturn(expected);

        service.extract(RAW_URL);

        verify(remoteResourceClient).getImage(URI.create("https://cdn.example.com/detail-1.jpg"));
        verify(remoteResourceClient).getImage(URI.create("https://cdn.example.com/detail-8.jpg"));
        verify(remoteResourceClient, never())
                .getImage(URI.create("https://cdn.example.com/detail-2.jpg"));
    }

    @Test
    void prioritizesInformationNamedDetailImages() {
        URI detail1 = URI.create("https://cdn.example.com/detail-1.jpg");
        URI detail2 = URI.create("https://cdn.example.com/detail-2.jpg");
        URI detail3 = URI.create("https://cdn.example.com/detail-3.jpg");
        URI materialTable = URI.create("https://cdn.example.com/material-table.jpg");
        URI detail5 = URI.create("https://cdn.example.com/detail-5.jpg");
        URI detail6 = URI.create("https://cdn.example.com/detail-6.jpg");
        URI detail7 = URI.create("https://cdn.example.com/detail-7.jpg");
        URI detail8 = URI.create("https://cdn.example.com/detail-8.jpg");
        ProductPageData page = new ProductPageData(
                PRODUCT_URL,
                "상품",
                "설명",
                PRIMARY_URL,
                List.of(detail1, detail2, detail3, materialTable, detail5, detail6, detail7, detail8),
                List.of());
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(page);
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(definitionRepository.findAll(any(Sort.class))).willReturn(List.of());
        given(geminiClient.extract(any(), anyList(), anyList()))
                .willReturn(new GeminiExtractionCandidate("상품", "TOP", List.of(), List.of()));
        ClothesExtractionDto expected = new ClothesExtractionDto(
                "상품", null, List.of(), PRIMARY_URL.toString(), List.of());
        given(extractionValidator.validate(any(), any(), anyList(), anyString())).willReturn(expected);

        service.extract(RAW_URL);

        verify(remoteResourceClient).getImage(materialTable);
    }

    @Test
    void primaryImageFailureIsReturnedAsPartialFailure() {
        ProductPageData page = new ProductPageData(
                PRODUCT_URL, "상품", "설명", PRIMARY_URL, List.of(), List.of());
        given(productUrlValidator.validate(RAW_URL)).willReturn(PRODUCT_URL);
        given(productPageExtractor.extract(PRODUCT_URL)).willReturn(page);
        given(remoteResourceClient.getImage(PRIMARY_URL))
                .willThrow(new BusinessException(com.otboo.clothes.exception.ClothesErrorCode.INVALID_REMOTE_IMAGE));
        given(definitionRepository.findAll(any(Sort.class))).willReturn(List.of());
        given(geminiClient.extract(any(), anyList(), anyList()))
                .willReturn(new GeminiExtractionCandidate("상품", "TOP", List.of(), List.of()));
        ClothesExtractionDto expected = new ClothesExtractionDto(
                "상품", null, List.of(), null, List.of());
        given(extractionValidator.validate(any(), any(), anyList(), org.mockito.ArgumentMatchers.isNull()))
                .willReturn(expected);

        ClothesExtractionDto result = service.extract(RAW_URL);

        assertThat(result.imageUrl()).isNull();
        assertThat(result.failures()).anySatisfy(failure ->
                assertThat(failure.field()).isEqualTo("image"));
    }

    @Test
    void extractionDoesNotOpenAnOuterTransaction() throws NoSuchMethodException {
        Method method = ClothesExtractionService.class.getMethod("extract", String.class);

        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }

    private ProductPageData pageWithDetails(int count) {
        List<URI> details = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> URI.create("https://cdn.example.com/detail-" + index + ".jpg"))
                .toList();
        return new ProductPageData(PRODUCT_URL, "상품", "설명", PRIMARY_URL, details, List.of());
    }

    private RemoteResource resource(URI uri) {
        return new RemoteResource(uri, "image/jpeg", new byte[]{1});
    }

    private ClothesAttributeDefinition mockDefinition() {
        ClothesAttributeDefinition definition = mock(ClothesAttributeDefinition.class);
        given(definition.getId()).willReturn(DEFINITION_ID);
        given(definition.getName()).willReturn("핏");
        return definition;
    }
}
