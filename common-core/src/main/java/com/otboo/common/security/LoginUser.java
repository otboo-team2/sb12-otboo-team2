package com.otboo.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 사용자를 주입한다.
 *
 * <pre>
 * {@code @PostMapping("/api/feeds")}
 * public FeedDto create({@code @LoginUser} AuthPrincipal me, {@code @RequestBody} FeedCreateRequest request) {
 *     // request.authorId() 를 쓰지 말 것. me.userId() 를 쓴다.
 * }
 * </pre>
 *
 * <p><b>요청 본문의 authorId · ownerId · followerId 는 절대 신뢰하지 않는다.</b>
 * 스펙상 그 값들이 body 에 들어 있지만, 그대로 쓰면 남의 이름으로 글을 쓸 수 있다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {

    /** false 로 두면 비로그인 요청에서 null 이 주입된다(공개 조회에서 likedByMe 판정 등). */
    boolean required() default true;
}
