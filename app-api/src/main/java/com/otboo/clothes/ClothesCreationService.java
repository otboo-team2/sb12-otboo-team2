package com.otboo.clothes;

import com.otboo.clothes.dto.ClothesCreateRequest;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.clothes.extraction.RemoteImageDownloader;
import com.otboo.common.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 직접 업로드와 구매 링크 이미지 등록을 기존 의상 저장 서비스에 연결한다. */
@Service
@RequiredArgsConstructor
public class ClothesCreationService {

    private final ClothesService clothesService;
    private final RemoteImageDownloader remoteImageDownloader;

    public ClothesDto create(
            UUID authenticatedUserId,
            ClothesCreateRequest request,
            MultipartFile image
    ) {
        if (!authenticatedUserId.equals(request.ownerId())) {
            throw new BusinessException(ClothesErrorCode.NOT_OWNER);
        }
        if (image != null && request.sourceImageUrl() != null) {
            throw new BusinessException(ClothesErrorCode.IMAGE_SOURCE_CONFLICT);
        }

        MultipartFile resolvedImage = image;
        if (resolvedImage == null && request.sourceImageUrl() != null) {
            resolvedImage = remoteImageDownloader.download(request.sourceImageUrl());
        }
        return clothesService.create(authenticatedUserId, request, resolvedImage);
    }
}
