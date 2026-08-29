package com.otboo.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@code @CreatedDate} / {@code @LastModifiedDate} 를 동작시킨다.
 * 이게 없으면 BaseEntity 의 시간 필드가 null 로 남아 insert 가 실패한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
