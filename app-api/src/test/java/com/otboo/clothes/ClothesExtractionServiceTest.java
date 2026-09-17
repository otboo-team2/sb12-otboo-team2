package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.clothes.dto.ClothesExtractionDto;
import com.otboo.clothes.entity.ClothesAttributeDefinition;
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
import com.otboo.common.exception.BusinessException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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

    private ClothesExtractionProperties properties;
    private ClothesExtractionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new ClothesExtractionProperties(
                "test-key", "gemini-test", 3, 2 * 1024 * 1024,
                10 * 1024 * 1024, 25 * 1024 * 1024, 4, 15_000);
        service = new ClothesExtractionService(
                productUrlValidator,
                productPageExtractor,
                remoteResourceClient,
                geminiClient,
                definitionRepository,
                selectableValueRepository,
                extractionValidator,
                properties);
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
        verify(remoteResourceClient).getImage(detail3);
        verify(geminiClient).extract(any(), org.mockito.ArgumentMatchers.argThat(images -> images.size() == 3), anyList());
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
