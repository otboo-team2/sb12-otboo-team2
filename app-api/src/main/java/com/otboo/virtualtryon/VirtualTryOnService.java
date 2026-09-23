package com.otboo.virtualtryon;

import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.storage.ImageStorage;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import com.otboo.virtualtryon.dto.VirtualTryOnJobResponse;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.otboo.virtualtryon.util.VirtualTryOnCacheKeyGenerator;
import com.otboo.virtualtryon.validation.VirtualTryOnImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VirtualTryOnService {

    private static final String DEFAULT_MODEL_HASH = "default-model-v1";
    private static final int MAX_REUSE_DEPTH = 5;
    private static final String MODEL_IMAGE_DIRECTORY = "virtual-try-on/models";

    @Value("${otboo.virtual-try-on.default-model-image-url}")
    private String defaultModelImageUrl;

    private final VirtualTryOnJobRepository jobRepository;
    private final VirtualTryOnCacheRepository cacheRepository;
    private final UserRepository userRepository;
    private final ClothesRepository clothesRepository;
    private final ImageStorage imageStorage;
    private final VirtualTryOnImageValidator imageValidator;
    private final Map<String, String> modelImageKeyCache = new ConcurrentHashMap<>();

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
        if (hasModelImage) {
            imageValidator.validate(modelImage);
        }
        String modelHash = hasModelImage ? sha256(modelImage) : DEFAULT_MODEL_HASH;

        VirtualTryOnJob job = VirtualTryOnJob.create(requester, top, bottom, additional, null, modelHash);

        // 상의 + 하의 + 추가 의상 다 같은 경우
        String cacheKey = VirtualTryOnCacheKeyGenerator.generate(modelHash, top.getId(), bottom.getId(), additionalId);
        var exact = cacheRepository.findExactMatch(requesterId, cacheKey);
        if (exact.isPresent()) {
            job.completeImmediately(exact.get());
            VirtualTryOnJob saved = jobRepository.save(job);
//            log.info("virtual_try_on_cache_hit jobId={} type=EXACT", saved.getId());
            return saved;
        }

        // 상의 + 하의는 같고 추가 의상만 다른 경우
        if (additional != null) {
            String rootCacheKey = VirtualTryOnCacheKeyGenerator.generate(modelHash, top.getId(), bottom.getId(), null);
            var exactRoot = cacheRepository.findExactMatch(requesterId, rootCacheKey);
            if (exactRoot.isPresent()) {
                job.startFrom(VirtualTryOnStep.ADDITIONAL, exactRoot.get());
                VirtualTryOnJob saved = jobRepository.save(job);
//                log.info("virtual_try_on_cache_hit jobId={} type=ROOT", saved.getId());
                return saved;
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
            VirtualTryOnJob saved = jobRepository.save(job);
//            log.info("virtual_try_on_cache_hit jobId={} type=PARTIAL startStep={}", saved.getId(), differingStep);
            return saved;
        }

        // 캐시 미스
        job.assignModelImage(hasModelImage
            ? resolveModelImageKey(requesterId, modelHash, modelImage)
            : defaultModelImageUrl);
        VirtualTryOnJob saved = jobRepository.save(job);
//        log.info("virtual_try_on_cache_hit jobId={} type=MISS", saved.getId());
        return saved;
    }

    /** 요청자 본인 소유의 job만 조회한다. 남의 job이거나 없으면 NOT_FOUND. */
    public VirtualTryOnJob findJob(UUID jobId, UUID requesterId) {
        return jobRepository.findByIdAndRequesterId(jobId, requesterId)
            .orElseThrow(() -> new BusinessException(VirtualTryOnErrorCode.NOT_FOUND));
    }

    /** job 엔티티를 응답 DTO로 변환한다. 결과 이미지 key → 응답용 URL 변환(resolveUrl)이 여기서 일어난다. */
    public VirtualTryOnJobResponse toResponse(VirtualTryOnJob job) {
        return VirtualTryOnJobResponse.from(job, imageStorage);
    }

    /** job을 조회하자마자 같은 트랜잭션 안에서 응답으로 변환한다. resultCache 같은 지연 로딩 필드는 조회한 세션 안에서만 접근할 수 있어서, 조회와 변환을 분리하면 안 된다. */
    @Transactional(readOnly = true)
    public VirtualTryOnJobResponse getJobResponse(UUID jobId, UUID requesterId) {
        VirtualTryOnJob job = findJob(jobId, requesterId);
        return toResponse(job);
    }

    /** 제출과 응답 변환을 한 트랜잭션에서 처리한다. 캐시 완전 일치로 즉시 완료되는 경우 resultCache 접근이 필요해서 마찬가지로 분리하면 안 된다. */
    @Transactional
    public VirtualTryOnJobResponse submitAndRespond(UUID requesterId, VirtualTryOnRequest request, MultipartFile modelImage) {
        VirtualTryOnJob job = submit(requesterId, request, modelImage);
        return toResponse(job);
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

    private String resolveModelImageKey(UUID requesterId, String modelHash, MultipartFile modelImage) {
        String cacheKey = requesterId + ":" + modelHash;
        return modelImageKeyCache.computeIfAbsent(cacheKey,
            key -> imageStorage.store(modelImage, MODEL_IMAGE_DIRECTORY));
    }
}
