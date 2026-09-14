package com.otboo.virtualtryon.repository;

import com.otboo.virtualtryon.entity.VirtualTryOnCache;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VirtualTryOnCacheRepository extends JpaRepository<VirtualTryOnCache, UUID> {

    @Query("""
    select c from VirtualTryOnCache c
    where c.requester.id = :requesterId
      and c.cacheKey = :cacheKey
    """)
    Optional<VirtualTryOnCache> findExactMatch(
        @Param("requesterId") UUID requesterId,
        @Param("cacheKey") String cacheKey);

    @Query("""
        select c from VirtualTryOnCache c
        where c.requester.id = :requesterId
          and c.modelHash = :modelHash
          and c.additionalClothes is null
          and ((c.topClothes.id = :topId and c.bottomClothes.id <> :bottomId)
            or (c.topClothes.id <> :topId and c.bottomClothes.id = :bottomId))
        order by c.generationDepth asc
        """)
    List<VirtualTryOnCache> findPartialMatchCandidates(
        @Param("requesterId") UUID requesterId,
        @Param("modelHash") String modelHash,
        @Param("topId") UUID topId,
        @Param("bottomId") UUID bottomId);
}
