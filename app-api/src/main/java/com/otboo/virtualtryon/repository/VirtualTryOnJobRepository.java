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
}
