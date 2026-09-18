package com.otboo.virtualtryon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.storage.ImageStorage;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import com.otboo.virtualtryon.dto.VirtualTryOnRequest;
import com.otboo.virtualtryon.entity.VirtualTryOnCache;
import com.otboo.virtualtryon.entity.VirtualTryOnJob;
import com.otboo.virtualtryon.entity.VirtualTryOnJobStatus;
import com.otboo.virtualtryon.entity.VirtualTryOnStep;
import com.otboo.virtualtryon.exception.VirtualTryOnErrorCode;
import com.otboo.virtualtryon.repository.VirtualTryOnCacheRepository;
import com.otboo.virtualtryon.repository.VirtualTryOnJobRepository;
import com.otboo.virtualtryon.util.VirtualTryOnCacheKeyGenerator;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import com.otboo.virtualtryon.validation.VirtualTryOnImageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Transactional
class VirtualTryOnServiceTest extends IntegrationTestSupport {

    private static final byte[] MODEL_IMAGE_CONTENT = "model-photo-bytes".getBytes();

    @MockitoBean
    VirtualTryOnImageValidator imageValidator;

    @Autowired
    VirtualTryOnService virtualTryOnService;

    @Autowired
    VirtualTryOnJobRepository jobRepository;

    @Autowired
    VirtualTryOnCacheRepository cacheRepository;

    @Autowired
    ClothesRepository clothesRepository;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    ImageStorage imageStorage;

    @Value("${otboo.virtual-try-on.default-model-image-url}")
    String defaultModelImageUrl;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        jobRepository.flush();
        cacheRepository.deleteAll();
        cacheRepository.flush();
        clothesRepository.deleteAll();
        clothesRepository.flush();
        userRepository.deleteAll();
        userRepository.flush();
    }

    @Test
    @DisplayName("캐시가 없으면 TOP부터 새 job을 만들고 모델 사진을 저장한다")
    void createsJobFromScratchWhenNoCacheMatches() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP);
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM);
        MockMultipartFile modelImage = modelImageFile();
        given(imageStorage.store(any(MultipartFile.class), eq("virtual-try-on/models")))
            .willReturn("/images/virtual-try-on/models/x.jpg");

        VirtualTryOnJob job = virtualTryOnService.submit(owner.getId(),
            new VirtualTryOnRequest(top.getId(), bottom.getId(), null), modelImage);

        assertThat(job.getStatus()).isEqualTo(VirtualTryOnJobStatus.PENDING);
        assertThat(job.getCurrentStep()).isEqualTo(VirtualTryOnStep.TOP);
        assertThat(job.getModelImageKey()).isEqualTo("/images/virtual-try-on/models/x.jpg");
        assertThat(job.getModelHash()).isEqualTo(sha256(MODEL_IMAGE_CONTENT));
        verify(imageStorage).store(modelImage, "virtual-try-on/models");
    }

    @Test
    @DisplayName("모델 사진을 안 올리면 기본 모델 이미지를 쓰고, 저장소는 호출하지 않는다")
    void usesDefaultModelWhenNoImageUploaded() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP);
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM);

        VirtualTryOnJob job = virtualTryOnService.submit(owner.getId(),
            new VirtualTryOnRequest(top.getId(), bottom.getId(), null), null);

        assertThat(job.getModelImageKey()).isEqualTo(defaultModelImageUrl);
        verify(imageStorage, never()).store(any(MultipartFile.class), any());
    }

    @Test
    @DisplayName("정확히 일치하는 캐시가 있으면 즉시 완료하고 모델 사진을 저장하지 않는다")
    void completesImmediatelyOnExactMatch() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP);
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM);
        String modelHash = sha256(MODEL_IMAGE_CONTENT);
        VirtualTryOnCache cache = saveCache(owner, modelHash, top, bottom, null, null, "/images/results/exact.jpg");

        VirtualTryOnJob job = virtualTryOnService.submit(owner.getId(),
            new VirtualTryOnRequest(top.getId(), bottom.getId(), null), modelImageFile());

        assertThat(job.getStatus()).isEqualTo(VirtualTryOnJobStatus.SUCCEEDED);
        assertThat(job.getCurrentStep()).isEqualTo(VirtualTryOnStep.DONE);
        assertThat(job.getResultCache().getId()).isEqualTo(cache.getId());
        verify(imageStorage, never()).store(any(MultipartFile.class), any());
    }

    @Test
    @DisplayName("상의+하의는 같고 추가 의상만 다르면 ADDITIONAL 단계부터 시작한다")
    void startsFromAdditionalStepOnRootMatch() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP);
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM);
        Clothes hat = saveClothes(owner, ClothesType.HAT);
        String modelHash = sha256(MODEL_IMAGE_CONTENT);
        VirtualTryOnCache root = saveCache(owner, modelHash, top, bottom, null, null, "/images/results/root.jpg");

        VirtualTryOnJob job = virtualTryOnService.submit(owner.getId(),
            new VirtualTryOnRequest(top.getId(), bottom.getId(), hat.getId()), modelImageFile());

        assertThat(job.getCurrentStep()).isEqualTo(VirtualTryOnStep.ADDITIONAL);
        assertThat(job.getReuseBaseCache().getId()).isEqualTo(root.getId());
        assertThat(job.getModelImageKey()).isEqualTo("/images/results/root.jpg");
        verify(imageStorage, never()).store(any(MultipartFile.class), any());
    }

    @Test
    @DisplayName("하의만 다르면 BOTTOM 단계만 다시 생성한다")
    void startsFromBottomStepWhenOnlyBottomDiffers() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP);
        Clothes oldBottom = saveClothes(owner, ClothesType.BOTTOM);
        Clothes newBottom = saveClothes(owner, ClothesType.BOTTOM);
        String modelHash = sha256(MODEL_IMAGE_CONTENT);
        saveCache(owner, modelHash, top, oldBottom, null, null, "/images/results/base.jpg");

        VirtualTryOnJob job = virtualTryOnService.submit(owner.getId(),
            new VirtualTryOnRequest(top.getId(), newBottom.getId(), null), modelImageFile());

        assertThat(job.getCurrentStep()).isEqualTo(VirtualTryOnStep.BOTTOM);
        assertThat(job.getModelImageKey()).isEqualTo("/images/results/base.jpg");
    }

    @Test
    @DisplayName("상의만 다르면 TOP 단계만 다시 생성한다 (재사용 기반이라 이후 BOTTOM은 안 만듦)")
    void startsFromTopStepWhenOnlyTopDiffers() {
        User owner = saveUser();
        Clothes oldTop = saveClothes(owner, ClothesType.TOP);
        Clothes newTop = saveClothes(owner, ClothesType.TOP);
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM);
        String modelHash = sha256(MODEL_IMAGE_CONTENT);
        saveCache(owner, modelHash, oldTop, bottom, null, null, "/images/results/base.jpg");

        VirtualTryOnJob job = virtualTryOnService.submit(owner.getId(),
            new VirtualTryOnRequest(newTop.getId(), bottom.getId(), null), modelImageFile());

        assertThat(job.getCurrentStep()).isEqualTo(VirtualTryOnStep.TOP);
        assertThat(job.getReuseBaseCache()).isNotNull();
    }

    @Test
    @DisplayName("본인 소유가 아닌 옷으로는 요청할 수 없다")
    void rejectsClothesNotOwnedByRequester() {
        User owner = saveUser();
        User stranger = saveUser();
        Clothes strangersTop = saveClothes(stranger, ClothesType.TOP);
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM);

        assertThatThrownBy(() -> virtualTryOnService.submit(owner.getId(),
            new VirtualTryOnRequest(strangersTop.getId(), bottom.getId(), null), null))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(VirtualTryOnErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("bottomClothesId 자리에 하의가 아닌 옷을 넣으면 거부한다")
    void rejectsWrongCategoryForBottomSlot() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP);
        Clothes notBottom = saveClothes(owner, ClothesType.TOP);

        assertThatThrownBy(() -> virtualTryOnService.submit(owner.getId(),
            new VirtualTryOnRequest(top.getId(), notBottom.getId(), null), null))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(VirtualTryOnErrorCode.CLOTHES_CATEGORY_MISMATCH));
    }

    @Test
    @DisplayName("추가 의상으로 드레스는 지정할 수 없다")
    void rejectsDressAsAdditionalClothes() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP);
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM);
        Clothes dress = saveClothes(owner, ClothesType.DRESS);

        assertThatThrownBy(() -> virtualTryOnService.submit(owner.getId(),
            new VirtualTryOnRequest(top.getId(), bottom.getId(), dress.getId()), null))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(VirtualTryOnErrorCode.UNSUPPORTED_CLOTHES_TYPE));
    }

    private MockMultipartFile modelImageFile() {
        return new MockMultipartFile("modelImage", "model.jpg", "image/jpeg", MODEL_IMAGE_CONTENT);
    }

    private User saveUser() {
        return userRepository.saveAndFlush(
            User.create("owner-%s@otboo.io".formatted(UUID.randomUUID()), "password", "사용자"));
    }

    private Clothes saveClothes(User owner, ClothesType type) {
        return clothesRepository.saveAndFlush(
            Clothes.create(owner.getId(), type.name() + "-" + UUID.randomUUID(), type, null));
    }

    private VirtualTryOnCache saveCache(User owner, String modelHash, Clothes top, Clothes bottom,
                                        Clothes additional, VirtualTryOnCache parent, String resultImageKey) {
        String cacheKey = VirtualTryOnCacheKeyGenerator.generate(modelHash, top.getId(), bottom.getId(),
            additional != null ? additional.getId() : null);
        return cacheRepository.saveAndFlush(VirtualTryOnCache.create(
            owner, cacheKey, modelHash, top, bottom, additional, parent, resultImageKey));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
