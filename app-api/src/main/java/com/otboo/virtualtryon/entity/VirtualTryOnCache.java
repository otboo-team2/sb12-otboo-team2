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
@Table(name = "virtual_try_on_caches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualTryOnCache extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(name = "cache_key", nullable = false, length = 200)
    private String cacheKey;

    @Column(name = "model_hash", nullable = false, length = 128)
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
    @JoinColumn(name = "parent_cache_id")
    private VirtualTryOnCache parentCache;

    @Column(name = "generation_depth", nullable = false)
    private byte generationDepth;

    @Column(name = "result_image_key", nullable = false, length = 500)
    private String resultImageKey;

    private VirtualTryOnCache(User requester, String cacheKey, String modelHash,
                              Clothes topClothes, Clothes bottomClothes, Clothes additionalClothes,
                              VirtualTryOnCache parentCache, String resultImageKey) {
        this.requester = requester;
        this.cacheKey = cacheKey;
        this.modelHash = modelHash;
        this.topClothes = topClothes;
        this.bottomClothes = bottomClothes;
        this.additionalClothes = additionalClothes;
        this.parentCache = parentCache;
        this.generationDepth = parentCache != null ? (byte) (parentCache.generationDepth + 1) : (byte) 1;
        this.resultImageKey = resultImageKey;
    }

    public static VirtualTryOnCache create(User requester, String cacheKey, String modelHash,
                                           Clothes topClothes, Clothes bottomClothes, Clothes additionalClothes,
                                           VirtualTryOnCache parentCache, String resultImageKey) {
        return new VirtualTryOnCache(requester, cacheKey, modelHash, topClothes, bottomClothes,
            additionalClothes, parentCache, resultImageKey);
    }
}
