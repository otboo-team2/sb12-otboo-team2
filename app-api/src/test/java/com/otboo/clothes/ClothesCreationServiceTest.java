package com.otboo.clothes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.clothes.dto.ClothesCreateRequest;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.extraction.RemoteImageDownloader;
import com.otboo.common.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ClothesCreationServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final String SOURCE_URL = "https://shop.example.com/products/1";

    @Mock ClothesService clothesService;
    @Mock RemoteImageDownloader remoteImageDownloader;

    private ClothesCreationService service;
    private ClothesCreateRequest directRequest;
    private ClothesDto expected;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClothesCreationService(clothesService, remoteImageDownloader);
        directRequest = new ClothesCreateRequest(
                OWNER_ID, "셔츠", ClothesType.TOP, List.of());
        expected = ClothesDto.withoutAttributes(
                com.otboo.clothes.entity.Clothes.create(
                        OWNER_ID, "셔츠", ClothesType.TOP, null));
    }

    @Test
    void delegatesDirectFileWithoutDownloading() {
        MultipartFile image = imageFile();
        given(clothesService.create(OWNER_ID, directRequest, image)).willReturn(expected);

        ClothesDto result = service.create(OWNER_ID, directRequest, image);

        assertThat(result).isSameAs(expected);
        verify(clothesService).create(OWNER_ID, directRequest, image);
        verifyNoInteractions(remoteImageDownloader);
    }

    @Test
    void delegatesWithoutImageOrSourceUrl() {
        given(clothesService.create(OWNER_ID, directRequest, null)).willReturn(expected);

        ClothesDto result = service.create(OWNER_ID, directRequest, null);

        assertThat(result).isSameAs(expected);
        verify(clothesService).create(OWNER_ID, directRequest, null);
        verifyNoInteractions(remoteImageDownloader);
    }

    @Test
    void downloadsSourceImageWhenDirectFileIsAbsent() {
        ClothesCreateRequest request = new ClothesCreateRequest(
                OWNER_ID, "셔츠", ClothesType.TOP, List.of(), SOURCE_URL);
        MultipartFile downloaded = imageFile();
        given(remoteImageDownloader.download(SOURCE_URL)).willReturn(downloaded);
        given(clothesService.create(OWNER_ID, request, downloaded)).willReturn(expected);

        ClothesDto result = service.create(OWNER_ID, request, null);

        assertThat(result).isSameAs(expected);
        verify(remoteImageDownloader).download(SOURCE_URL);
        verify(clothesService).create(OWNER_ID, request, downloaded);
    }

    @Test
    void rejectsDirectFileAndSourceUrlTogetherBeforeDownloading() {
        ClothesCreateRequest request = new ClothesCreateRequest(
                OWNER_ID, "셔츠", ClothesType.TOP, List.of(), SOURCE_URL);

        assertThatThrownBy(() -> service.create(OWNER_ID, request, imageFile()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.IMAGE_SOURCE_CONFLICT));

        verifyNoInteractions(remoteImageDownloader, clothesService);
    }

    @Test
    void rejectsDifferentOwnerBeforeDownloading() {
        ClothesCreateRequest request = new ClothesCreateRequest(
                UUID.randomUUID(), "셔츠", ClothesType.TOP, List.of(), SOURCE_URL);

        assertThatThrownBy(() -> service.create(OWNER_ID, request, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ClothesErrorCode.NOT_OWNER));

        verifyNoInteractions(remoteImageDownloader, clothesService);
    }

    @Test
    void propagatesDownstreamCreationFailure() {
        given(clothesService.create(eq(OWNER_ID), eq(directRequest), any(MultipartFile.class)))
                .willThrow(new BusinessException(ClothesErrorCode.CLOTHES_NOT_FOUND));

        assertThatThrownBy(() -> service.create(OWNER_ID, directRequest, imageFile()))
                .isInstanceOf(BusinessException.class);

        verify(clothesService).create(eq(OWNER_ID), eq(directRequest), any(MultipartFile.class));
        verify(remoteImageDownloader, never()).download(any());
    }

    private MultipartFile imageFile() {
        return new MockMultipartFile("image", "shirt.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
