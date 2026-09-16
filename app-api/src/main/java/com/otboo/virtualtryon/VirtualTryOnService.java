package com.otboo.virtualtryon;

import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.storage.ImageStorage;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import com.otboo.virtualtryon.dto.VirtualTryOnRequest;
import com.otboo.virtualtryon.entity.VirtualTryOnCache;
import com.otboo.virtualtryon.entity.VirtualTryOnJob;
import com.otboo.virtualtryon.entity.VirtualTryOnStep;
import com.otboo.virtualtryon.exception.VirtualTryOnErrorCode;
import com.otboo.virtualtryon.repository.VirtualTryOnCacheRepository;
import com.otboo.virtualtryon.repository.VirtualTryOnJobRepository;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.otboo.virtualtryon.util.VirtualTryOnCacheKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VirtualTryOnService {

    private static final String DEFAULT_MODEL_HASH = "default-model-v1";
    private static final int MAX_REUSE_DEPTH = 5;

    @Value("${otboo.virtual-try-on.default-model-image-url}")
    private String defaultModelImageUrl;

    private final VirtualTryOnJobRepository jobRepository;
    private final VirtualTryOnCacheRepository cacheRepository;
    private final UserRepository userRepository;
    private final ClothesRepository clothesRepository;
    private final ImageStorage imageStorage;

    /**
     * 가상피팅 요청을 받아 job을 만든다.
     * 캐시를 4단계로 확인해서, 재사용 가능한 만큼 FASHN 호출을 건너뛰고 이어서 시작할 지점을 정한다.
     * (완전 일치 → 즉시 완료 / 루트만 일치 → ADDITIONAL부터 / 부분 일치 → TOP or BOTTOM부터 / 미스 → 처음부터)
     */
    @Transactional
    public VirtualTryOnJob submit(UUID requesterId, VirtualTryOnRequest request, MultipartFile modelImage) {
        User requester = userRepository.getReferenceById(requesterId);
        Clothes top = findOwnedClothes(request.topClothesId(), requesterId, ClothesType.TOP);
        Clothes bottom = findOwnedClothes(request.bottomClothesId(), requesterId, ClothesType.BOTTOM);
        Clothes additional = request.additionalClothesId() != null
            ? findOwnedAdditional(request.additionalClothesId(), requesterId) : null;
        UUID additionalId = additional != null ? additional.getId() : null;

        boolean hasModelImage = modelImage != null && !modelImage.isEmpty();
        String modelHash = hasModelImage ? sha256(modelImage) : DEFAULT_MODEL_HASH;

        VirtualTryOnJob job = VirtualTryOnJob.create(requester, top, bottom, additional, null, modelHash);

        // 상의 + 하의 + 추가 의상 다 같은 경우
        String cacheKey = VirtualTryOnCacheKeyGenerator.generate(modelHash, top.getId(), bottom.getId(), additionalId);
        var exact = cacheRepository.findExactMatch(requesterId, cacheKey);
        if (exact.isPresent()) {
            job.completeImmediately(exact.get());
            return jobRepository.save(job);
        }

        // 상의 + 하의는 같고 추가 의상만 다른 경우
        if (additional != null) {
            String rootCacheKey = VirtualTryOnCacheKeyGenerator.generate(modelHash, top.getId(), bottom.getId(), null);
            var exactRoot = cacheRepository.findExactMatch(requesterId, rootCacheKey);
            if (exactRoot.isPresent()) {
                job.startFrom(VirtualTryOnStep.ADDITIONAL, exactRoot.get());
                return jobRepository.save(job);
            }
        }

        // 상의나 하의 중 하나만 같은 경우
        List<VirtualTryOnCache> partial = cacheRepository.findPartialMatchCandidates(
            requesterId, modelHash, top.getId(), bottom.getId());
        var reusable = partial.stream()
            .filter(c -> c.getGenerationDepth() < MAX_REUSE_DEPTH)
            .findFirst();
        if (reusable.isPresent()) {
            VirtualTryOnCache base = reusable.get();
            VirtualTryOnStep differingStep = base.getTopClothes().getId().equals(top.getId())
                ? VirtualTryOnStep.BOTTOM : VirtualTryOnStep.TOP;
            job.startFrom(differingStep, base);
            return jobRepository.save(job);
        }

        // 캐시 미스
        job.assignModelImage(hasModelImage
            ? imageStorage.store(modelImage, "virtual-try-on/models")
            : defaultModelImageUrl);
        return jobRepository.save(job);
    }

    /** 요청자 본인 소유의 job만 조회한다. 남의 job이거나 없으면 NOT_FOUND. */
    public VirtualTryOnJob findJob(UUID jobId, UUID requesterId) {
        return jobRepository.findByIdAndRequesterId(jobId, requesterId)
            .orElseThrow(() -> new BusinessException(VirtualTryOnErrorCode.NOT_FOUND));
    }

    /** 본인 소유이면서 타입이 예상(TOP/BOTTOM)과 맞는 옷을 찾는다. 소유자가 다르거나 타입이 다르면 예외. */
    private Clothes findOwnedClothes(UUID clothesId, UUID ownerId, ClothesType expected) {
        Clothes clothes = clothesRepository.findByIdAndOwnerId(clothesId, ownerId)
            .orElseThrow(() -> new BusinessException(VirtualTryOnErrorCode.NOT_FOUND));
        if (clothes.getType() != expected) {
            throw new BusinessException(VirtualTryOnErrorCode.CLOTHES_CATEGORY_MISMATCH)
                .addDetail("expected", expected.name())
                .addDetail("actual", clothes.getType().name());
        }
        return clothes;
    }

    /** 본인 소유의 추가 의상을 찾는다. 드레스는 추가 의상으로 지정할 수 없다. */
    private Clothes findOwnedAdditional(UUID clothesId, UUID ownerId) {
        Clothes clothes = clothesRepository.findByIdAndOwnerId(clothesId, ownerId)
            .orElseThrow(() -> new BusinessException(VirtualTryOnErrorCode.NOT_FOUND));
        if (clothes.getType() == ClothesType.DRESS) {
            throw new BusinessException(VirtualTryOnErrorCode.UNSUPPORTED_CLOTHES_TYPE);
        }
        return clothes;
    }

    /** 모델 사진 파일 내용을 스트리밍으로 읽어 SHA-256 해시를 만든다. 캐시 키 생성에 쓰인다. */
    private String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
