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
import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.DispatchTarget;
import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.PollTarget;
import com.otboo.virtualtryon.entity.VirtualTryOnCache;
import com.otboo.virtualtryon.entity.VirtualTryOnJob;
import com.otboo.virtualtryon.entity.VirtualTryOnJobStatus;
import com.otboo.virtualtryon.entity.VirtualTryOnStep;
import com.otboo.virtualtryon.exception.VirtualTryOnErrorCode;
import com.otboo.virtualtryon.repository.VirtualTryOnCacheRepository;
import com.otboo.virtualtryon.repository.VirtualTryOnJobRepository;
import com.otboo.virtualtryon.util.VirtualTryOnCacheKeyGenerator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class VirtualTryOnJobServiceTest extends IntegrationTestSupport {

    @Autowired
    VirtualTryOnJobTransactionService transactionService;

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

        given(imageStorage.readAsDataUri(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("PENDING job을 limit만큼 선점하고 PROCESSING으로 바꾼다")
    void claimsPendingJobsUpToLimit() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        saveJob(owner, top, bottom, null, "model.jpg", "hash");
        saveJob(owner, top, bottom, null, "model.jpg", "hash");
        saveJob(owner, top, bottom, null, "model.jpg", "hash");

        List<UUID> claimed = transactionService.claimPendingJobs(2);

        assertThat(claimed).hasSize(2);
        assertThat(jobRepository.findAllByStatus(VirtualTryOnJobStatus.PROCESSING)).hasSize(2);
        assertThat(jobRepository.findAllByStatus(VirtualTryOnJobStatus.PENDING)).hasSize(1);
    }

    @Test
    @DisplayName("PROCESSING 상태인 job의 id만 돌려준다")
    void findsOnlyProcessingJobIds() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        VirtualTryOnJob processing1 = saveJob(owner, top, bottom, null, "model.jpg", "hash");
        transactionService.markRequested(processing1.getId(), "pred-1");
        VirtualTryOnJob processing2 = saveJob(owner, top, bottom, null, "model.jpg", "hash");
        transactionService.markRequested(processing2.getId(), "pred-2");
        saveJob(owner, top, bottom, null, "model.jpg", "hash");

        List<UUID> ids = transactionService.findProcessingJobIds();

        assertThat(ids).containsExactlyInAnyOrder(processing1.getId(), processing2.getId());
    }

    @Test
    @DisplayName("TOP 단계면 상의 이미지를, BOTTOM 단계면 하의 이미지를 디스패치 대상으로 잡는다")
    void loadsDispatchTargetForCurrentStep() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, null, "model.jpg", "hash");

        DispatchTarget topTarget = transactionService.loadDispatchTarget(job.getId());
        assertThat(topTarget.modelImage()).isEqualTo("model.jpg");
        assertThat(topTarget.productImage()).isEqualTo("/top.jpg");

        job.advanceWithoutCaching(VirtualTryOnStep.BOTTOM, "/images/results/after-top.jpg");
        jobRepository.saveAndFlush(job);

        DispatchTarget bottomTarget = transactionService.loadDispatchTarget(job.getId());
        assertThat(bottomTarget.modelImage()).isEqualTo("/images/results/after-top.jpg");
        assertThat(bottomTarget.productImage()).isEqualTo("/bottom.jpg");
    }

    @Test
    @DisplayName("FASHN prediction id를 기록하고 PROCESSING으로 바꾼다")
    void marksJobAsRequested() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, null, "model.jpg", "hash");

        transactionService.markRequested(job.getId(), "predictionId");

        VirtualTryOnJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getFashnPredictionId()).isEqualTo("predictionId");
        assertThat(reloaded.getStatus()).isEqualTo(VirtualTryOnJobStatus.PROCESSING);
    }

    @Test
    @DisplayName("생성 시각과 prediction id를 폴링 대상으로 돌려준다")
    void loadsPollTarget() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, null, "model.jpg", "hash");
        transactionService.markRequested(job.getId(), "predictionId");

        PollTarget target = transactionService.loadPollTarget(job.getId());

        assertThat(target.predictionId()).isEqualTo("predictionId");
        assertThat(target.createdAt())
            .isEqualTo(jobRepository.findById(job.getId()).orElseThrow().getCreatedAt());
    }

    @Test
    @DisplayName("TOP 결과 도착 시, 재사용 기반이 아니면 캐싱 없이 BOTTOM으로 넘어간다")
    void advancesToBottomWithoutCachingWhenTopHasNoReuseBase() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, null, "model.jpg", "hash");

        transactionService.applyResult(job.getId(), "https://fashn.ai/output/top-result.jpg");

        VirtualTryOnJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStep()).isEqualTo(VirtualTryOnStep.BOTTOM);
        assertThat(reloaded.getModelImageKey()).isEqualTo("https://fashn.ai/output/top-result.jpg");
        assertThat(reloaded.getStatus()).isEqualTo(VirtualTryOnJobStatus.PENDING);
        assertThat(reloaded.getFashnPredictionId()).isNull();
        verify(imageStorage, never()).storeFromUrl(any(), any());
    }

    @Test
    @DisplayName("TOP 결과 도착 시, 재사용 기반이면 BOTTOM을 새로 만들지 않고 바로 완료 처리한다")
    void completesImmediatelyWhenTopResultHasReuseBase() {
        User owner = saveUser();
        Clothes oldTop = saveClothes(owner, ClothesType.TOP, "/old-top.jpg");
        Clothes newTop = saveClothes(owner, ClothesType.TOP, "/new-top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        VirtualTryOnCache baseCache =
            saveCache(owner, "hash", oldTop, bottom, null, null, "/images/results/base.jpg");
        VirtualTryOnJob job = saveJob(owner, newTop, bottom, null, "model.jpg", "hash");
        job.startFrom(VirtualTryOnStep.TOP, baseCache);
        jobRepository.saveAndFlush(job);
        given(imageStorage.storeFromUrl(any(), eq("virtual-try-on/results")))
            .willReturn("/images/results/new-top-applied.jpg");

        transactionService.applyResult(job.getId(), "https://fashn.ai/output/new-top.jpg");

        VirtualTryOnJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStep()).isEqualTo(VirtualTryOnStep.DONE);
        assertThat(reloaded.getStatus()).isEqualTo(VirtualTryOnJobStatus.SUCCEEDED);
        assertThat(reload(reloaded.getResultCache()).getResultImageKey())
            .isEqualTo("/images/results/new-top-applied.jpg");
        verify(imageStorage).storeFromUrl("https://fashn.ai/output/new-top.jpg", "virtual-try-on/results");
    }

    @Test
    @DisplayName("BOTTOM 결과 도착 시, 추가 의상이 없으면 캐싱하고 바로 완료한다")
    void completesRootOnBottomResultWithoutAdditional() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, null, "model.jpg", "hash");
        job.advanceWithoutCaching(VirtualTryOnStep.BOTTOM, "/images/results/after-top.jpg");
        jobRepository.saveAndFlush(job);
        given(imageStorage.storeFromUrl(any(), eq("virtual-try-on/results")))
            .willReturn("/images/results/root.jpg");

        transactionService.applyResult(job.getId(), "https://fashn.ai/output/bottom-result.jpg");

        VirtualTryOnJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStep()).isEqualTo(VirtualTryOnStep.DONE);
        assertThat(reloaded.getStatus()).isEqualTo(VirtualTryOnJobStatus.SUCCEEDED);
        assertThat(reload(reloaded.getResultCache()).getResultImageKey()).isEqualTo("/images/results/root.jpg");
    }

    @Test
    @DisplayName("BOTTOM 결과 도착 시, 추가 의상이 있으면 캐싱만 하고 ADDITIONAL로 넘어간다")
    void movesToAdditionalOnBottomResultWithAdditional() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        Clothes hat = saveClothes(owner, ClothesType.HAT, "/hat.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, hat, "model.jpg", "hash");
        job.advanceWithoutCaching(VirtualTryOnStep.BOTTOM, "/images/results/after-top.jpg");
        jobRepository.saveAndFlush(job);
        given(imageStorage.storeFromUrl(any(), eq("virtual-try-on/results")))
            .willReturn("/images/results/root.jpg");

        transactionService.applyResult(job.getId(), "https://fashn.ai/output/bottom-result.jpg");

        VirtualTryOnJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStep()).isEqualTo(VirtualTryOnStep.ADDITIONAL);
        assertThat(reloaded.getStatus()).isEqualTo(VirtualTryOnJobStatus.PENDING);
        assertThat(reloaded.getFashnPredictionId()).isNull();
        assertThat(reloaded.getModelImageKey()).isEqualTo("/images/results/root.jpg");
        assertThat(reloaded.getReuseBaseCache()).isNotNull();
    }

    @Test
    @DisplayName("ADDITIONAL 결과 도착 시, 캐싱하고 완료 처리한다")
    void completesOnAdditionalResult() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        Clothes hat = saveClothes(owner, ClothesType.HAT, "/hat.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, hat, "model.jpg", "hash");
        job.advanceWithoutCaching(VirtualTryOnStep.ADDITIONAL, "/images/results/root.jpg");
        jobRepository.saveAndFlush(job);
        given(imageStorage.storeFromUrl(any(), eq("virtual-try-on/results")))
            .willReturn("/images/results/final.jpg");

        transactionService.applyResult(job.getId(), "https://fashn.ai/output/additional-result.jpg");

        VirtualTryOnJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getCurrentStep()).isEqualTo(VirtualTryOnStep.DONE);
        assertThat(reloaded.getStatus()).isEqualTo(VirtualTryOnJobStatus.SUCCEEDED);
        VirtualTryOnCache resultCache = reload(reloaded.getResultCache());
        assertThat(resultCache.getAdditionalClothes().getId()).isEqualTo(hat.getId());
        assertThat(resultCache.getResultImageKey()).isEqualTo("/images/results/final.jpg");
    }

    @Test
    @DisplayName("이미 DONE인 job에 결과가 오면 예외를 던진다")
    void throwsWhenApplyingResultToDoneJob() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, null, "model.jpg", "hash");
        job.fail();
        jobRepository.saveAndFlush(job);

        assertThatThrownBy(() -> transactionService.applyResult(job.getId(), "https://fashn.ai/output/x.jpg"))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(VirtualTryOnErrorCode.INVALID_JOB_STATE));
    }

    @Test
    @DisplayName("동시에 같은 캐시를 만들려다 유니크 제약에 걸리면, 기존 캐시를 재사용하고 방금 올린 이미지는 지운다")
    void reusesExistingCacheOnUniqueConstraintRace() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        String modelHash = "shared-hash";
        VirtualTryOnCache existing =
            saveCache(owner, modelHash, top, bottom, null, null, "/images/results/existing.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, null, "model.jpg", modelHash);
        job.advanceWithoutCaching(VirtualTryOnStep.BOTTOM, "/images/results/after-top.jpg");
        jobRepository.saveAndFlush(job);
        given(imageStorage.storeFromUrl(any(), eq("virtual-try-on/results")))
            .willReturn("/images/results/orphaned-duplicate.jpg");

        transactionService.applyResult(job.getId(), "https://fashn.ai/output/duplicate.jpg");

        VirtualTryOnJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getResultCache().getId()).isEqualTo(existing.getId());
        assertThat(cacheRepository.count()).isEqualTo(1);
        verify(imageStorage).delete("/images/results/orphaned-duplicate.jpg");
    }

    @Test
    @DisplayName("실패 처리하면 FAILED/DONE으로 바꾼다")
    void failsJob() {
        User owner = saveUser();
        Clothes top = saveClothes(owner, ClothesType.TOP, "/top.jpg");
        Clothes bottom = saveClothes(owner, ClothesType.BOTTOM, "/bottom.jpg");
        VirtualTryOnJob job = saveJob(owner, top, bottom, null, "model.jpg", "hash");

        transactionService.failJob(job.getId());

        VirtualTryOnJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(VirtualTryOnJobStatus.FAILED);
        assertThat(reloaded.getCurrentStep()).isEqualTo(VirtualTryOnStep.DONE);
    }

    private User saveUser() {
        return userRepository.saveAndFlush(
            User.create("owner-%s@otboo.io".formatted(UUID.randomUUID()), "password", "사용자"));
    }

    private Clothes saveClothes(User owner, ClothesType type, String imageUrl) {
        return clothesRepository.saveAndFlush(
            Clothes.create(owner.getId(), type.name() + "-" + UUID.randomUUID(), type, imageUrl));
    }

    private VirtualTryOnJob saveJob(User owner, Clothes top, Clothes bottom, Clothes additional,
                                    String modelImageKey, String modelHash) {
        return jobRepository.saveAndFlush(
            VirtualTryOnJob.create(owner, top, bottom, additional, modelImageKey, modelHash));
    }

    private VirtualTryOnCache saveCache(User owner, String modelHash, Clothes top, Clothes bottom,
                                        Clothes additional, VirtualTryOnCache parent, String resultImageKey) {
        String cacheKey = VirtualTryOnCacheKeyGenerator.generate(modelHash, top.getId(), bottom.getId(),
            additional != null ? additional.getId() : null);
        return cacheRepository.saveAndFlush(VirtualTryOnCache.create(
            owner, cacheKey, modelHash, top, bottom, additional, parent, resultImageKey));
    }

    private VirtualTryOnCache reload(VirtualTryOnCache cache) {
        return cacheRepository.findById(cache.getId()).orElseThrow();
    }
}
