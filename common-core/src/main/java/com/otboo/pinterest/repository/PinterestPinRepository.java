package com.otboo.pinterest.repository;

import com.otboo.pinterest.entity.PinterestPin;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PinterestPinRepository extends JpaRepository<PinterestPin, UUID> {

    Optional<PinterestPin> findByPinId(String pinId);

    /** 동기화 한 번에 받은 핀들의 기존 행을 한 번에 가져온다. 핀마다 조회하면 N+1 이 된다. */
    List<PinterestPin> findAllByPinIdIn(Collection<String> pinIds);
}
