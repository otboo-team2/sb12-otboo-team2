package com.otboo.virtualtryon;

import com.otboo.virtualtryon.entity.VirtualTryOnCache;
import com.otboo.virtualtryon.repository.VirtualTryOnCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐시 저장 시도를 별도 트랜잭션으로 분리한다.
 * cache_key 유니크 제약 위반은 같은 트랜잭션 안에서 catch 해도 이미 rollback-only로 표시되어
 * 커밋 시점에 UnexpectedRollbackException이 난다. REQUIRES_NEW로 격리해서
 * 실패해도 호출자(VirtualTryOnJobTransactionService)가 진행 중인 트랜잭션에 영향을 안 주게 한다.
 */
@Service
@RequiredArgsConstructor
public class VirtualTryOnCacheWriter {

    private final VirtualTryOnCacheRepository cacheRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VirtualTryOnCache save(VirtualTryOnCache cache) {
        return cacheRepository.saveAndFlush(cache);
    }
}
