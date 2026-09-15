package com.otboo.virtualtryon.entity;

import com.otboo.clothes.entity.Clothes;
import com.otboo.common.entity.BaseEntity;
import com.otboo.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "virtual_try_on_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualTryOnJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VirtualTryOnJobStatus status;

    @Column(name = "fashn_prediction_id", length = 100)
    private String fashnPredictionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 32)
    private VirtualTryOnStep currentStep;

    @Column(name = "model_image_key", length = 500)
    private String modelImageKey;

    @Column(name = "model_hash", length = 128)
    private String modelHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "top_clothes_id", nullable = false)
    private Clothes topClothes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bottom_clothes_id", nullable = false)
    private Clothes bottomClothes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "additional_clothes_id")
    private Clothes additionalClothes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_cache_id")
    private VirtualTryOnCache resultCache;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reuse_base_cache_id")
    private VirtualTryOnCache reuseBaseCache;

    private VirtualTryOnJob(User requester, Clothes topClothes, Clothes bottomClothes,
                            Clothes additionalClothes, String modelImageKey, String modelHash) {
        this.requester = requester;
        this.topClothes = topClothes;
        this.bottomClothes = bottomClothes;
        this.additionalClothes = additionalClothes;
        this.modelImageKey = modelImageKey;
        this.modelHash = modelHash;
        this.status = VirtualTryOnJobStatus.PENDING;
        this.currentStep = VirtualTryOnStep.TOP;
    }

    public static VirtualTryOnJob create(User requester, Clothes topClothes, Clothes bottomClothes,
                                         Clothes additionalClothes, String modelImageKey, String modelHash) {
        return new VirtualTryOnJob(requester, topClothes, bottomClothes, additionalClothes,
            modelImageKey, modelHash);
    }

    public void startFrom(VirtualTryOnStep step, VirtualTryOnCache base) {
        this.currentStep = step;
        this.modelImageKey = base.getResultImageKey();
        this.reuseBaseCache = base;
    }

    public void completeImmediately(VirtualTryOnCache cache) {
        this.resultCache = cache;
        this.currentStep = VirtualTryOnStep.DONE;
        this.status = VirtualTryOnJobStatus.SUCCEEDED;
    }

    public void markRequested(String fashnPredictionId) {
        this.fashnPredictionId = fashnPredictionId;
        this.status = VirtualTryOnJobStatus.PROCESSING;
    }

    public void rootReady(VirtualTryOnCache rootCache) {
        this.reuseBaseCache = rootCache;
        if (additionalClothes == null) {
            succeed(rootCache);
        } else {
            this.currentStep = VirtualTryOnStep.ADDITIONAL;
            this.modelImageKey = rootCache.getResultImageKey();
            this.status = VirtualTryOnJobStatus.PENDING;
            this.fashnPredictionId = null;
        }
    }

    public void advanceWithoutCaching(VirtualTryOnStep nextStep, String intermediateImageUrl) {
        this.currentStep = nextStep;
        this.modelImageKey = intermediateImageUrl;
        this.status = VirtualTryOnJobStatus.PENDING;
        this.fashnPredictionId = null;
    }

    public void succeed(VirtualTryOnCache resultCache) {
        this.resultCache = resultCache;
        this.currentStep = VirtualTryOnStep.DONE;
        this.status = VirtualTryOnJobStatus.SUCCEEDED;
    }

    public void fail() {
        this.status = VirtualTryOnJobStatus.FAILED;
        this.currentStep = VirtualTryOnStep.DONE;
    }

    public boolean isModelImageFromFashn() {
        return currentStep == VirtualTryOnStep.BOTTOM && reuseBaseCache == null;
    }
}
