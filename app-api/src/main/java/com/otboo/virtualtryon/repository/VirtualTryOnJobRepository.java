package com.otboo.virtualtryon.repository;

import com.otboo.virtualtryon.entity.VirtualTryOnJob;
import com.otboo.virtualtryon.entity.VirtualTryOnJobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualTryOnJobRepository extends JpaRepository<VirtualTryOnJob, UUID> {
    Optional<VirtualTryOnJob> findByIdAndRequesterId(UUID id, UUID requesterId);
    List<VirtualTryOnJob> findAllByStatus(VirtualTryOnJobStatus status);
}
