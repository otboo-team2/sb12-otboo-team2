package com.otboo.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 엔티티의 부모. 각 파트는 자기 엔티티에서 이 클래스를 상속한다.
 *
 * <pre>
 *   {@code @Entity}
 *   {@code @Table(name = "clothes_tb")}
 *   public class Clothes extends BaseEntity { ... }
 * </pre>
 *
 * <h2>논리삭제를 쓰지 않는다 (2026-08-29 확정)</h2>
 * 하드 삭제가 기본이다. Swagger 스펙상 {@code users} 에 DELETE 가 없어 복구 요구 자체가 없고,
 * {@code deletedAt} 을 두면 <b>모든 조회에 {@code AND deleted_at IS NULL} 이 붙고</b>
 * 이메일 같은 유니크 제약도 깨진다. 알림의 "읽음 처리"만 상태 플래그로 다룬다.
 *
 * <h2>시간은 {@link Instant} 로 다룬다</h2>
 * {@code LocalDateTime} 은 타임존 정보가 없어서 어디선가 한 번 어긋나면 추적이 안 된다.
 * 저장·전송 모두 UTC 기준이고, 표시 시점의 변환은 클라이언트 몫이다.
 *
 * <h2>PK 는 UUID / BINARY(16)</h2>
 * API 스펙이 UUID 라 저장도 UUID 로 간다. {@code CHAR(36)} 대신 {@code BINARY(16)} 인 이유는
 * InnoDB 에서 <b>모든 보조 인덱스가 PK 를 함께 저장</b>하기 때문에 20바이트 차이가 인덱스 전체에 곱해지기 때문이다.
 * 랜덤 UUID 라 삽입 위치가 흩어지는 문제(페이지 분할)가 있는데, 이건 부하 테스트로 확인된 뒤에 다룬다.
 * 컬럼 타입을 16바이트로 잡아뒀으므로 <b>생성 전략만 시간순 UUID 로 바꾸면 되고 스키마는 그대로다.</b>
 *
 * <p>디버깅 시 사람이 읽으려면: {@code SELECT BIN_TO_UUID(id) FROM clothes_tb;}
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 프록시와 실제 엔티티가 섞여도 같은 행이면 같다고 판단한다.
     * id 가 아직 없는(영속화 전) 엔티티는 서로 다른 것으로 본다.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> thisType = unproxyClass(this);
        Class<?> otherType = unproxyClass(o);
        if (!thisType.equals(otherType)) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        return this.id != null && Objects.equals(this.id, other.getId());
    }

    /**
     * id 가 나중에 채워져도 해시가 변하지 않도록 클래스 기준으로 고정한다.
     * id 기반으로 만들면 영속화 전후로 값이 바뀌어 Set/Map 에서 엔티티를 잃어버린다.
     */
    @Override
    public final int hashCode() {
        return unproxyClass(this).hashCode();
    }

    private static Class<?> unproxyClass(Object entity) {
        return entity instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : entity.getClass();
    }
}
