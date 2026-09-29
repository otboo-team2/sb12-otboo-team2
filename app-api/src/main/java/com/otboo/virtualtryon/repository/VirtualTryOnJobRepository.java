package com.otboo.virtualtryon.repository;

import com.otboo.virtualtryon.entity.VirtualTryOnJob;
import com.otboo.virtualtryon.entity.VirtualTryOnJobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VirtualTryOnJobRepository extends JpaRepository<VirtualTryOnJob, UUID> {
    Optional<VirtualTryOnJob> findByIdAndRequesterId(UUID id, UUID requesterId);
    List<VirtualTryOnJob> findAllByStatus(VirtualTryOnJobStatus status);

    @Query(value =
        "select id from virtual_try_on_jobs where status = 'PENDING' "
            + "order by created_at limit :limit for update skip locked", nativeQuery = true)
    List<String> lockPendingJobIds(@Param("limit") int limit);

    @Modifying
    @Query("update VirtualTryOnJob j set j.status = com.otboo.virtualtryon.entity.VirtualTryOnJobStatus.PROCESSING "
        + "where j.id in :ids")
    void markProcessing(@Param("ids") List<UUID> ids);

    /** PENDING 일 때만 PROCESSING 으로 바꾼다. 바뀐 행 수가 1이면 내가 가져간 것, 0이면 이미 다른 쪽이 가져갔다. */
    @Modifying
    @Query("update VirtualTryOnJob j set j.status = com.otboo.virtualtryon.entity.VirtualTryOnJobStatus.PROCESSING "
        + "where j.id = :id and j.status = com.otboo.virtualtryon.entity.VirtualTryOnJobStatus.PENDING")
    int claimIfPending(@Param("id") UUID id);

    Optional<VirtualTryOnJob> findByFashnPredictionId(String fashnPredictionId);

    /** 이 prediction 의 결과를 반영할 권한을 가져간다. prediction id 를 비워서 웹훅 재전송이나 폴링이 같은 결과를 두 번 반영하지 못하게 한다. */
    @Modifying
    @Query("update VirtualTryOnJob j set j.fashnPredictionId = null "
        + "where j.fashnPredictionId = :predictionId "
        + "and j.status = com.otboo.virtualtryon.entity.VirtualTryOnJobStatus.PROCESSING")
    int clearPredictionIfProcessing(@Param("predictionId") String predictionId);
}
