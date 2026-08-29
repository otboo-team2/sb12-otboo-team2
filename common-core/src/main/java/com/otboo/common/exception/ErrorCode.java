package com.otboo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 도메인 에러 코드 enum 이 구현하는 인터페이스.
 *
 * <p>각 파트는 자기 도메인 패키지에 enum 을 만들어 이 인터페이스를 구현한다.
 * 공통 파일 하나에 5명이 몰리면 머지 충돌이 계속 난다.
 *
 * <p>코드 형식: {@code {DOMAIN}_{NNN}}
 * <ul>
 *   <li>001~099 : 검증 / 잘못된 요청 (400)</li>
 *   <li>100~199 : 인증 · 인가 (401 / 403)</li>
 *   <li>200~299 : 리소스 없음 (404)</li>
 *   <li>300~399 : 충돌 · 중복 (409)</li>
 *   <li>900~999 : 외부 연동 · 서버 오류 (5xx)</li>
 * </ul>
 */
public interface ErrorCode {

    /** 응답의 exceptionName 에 실리는 값. 예: {@code CLOTHES_201} */
    String getCode();

    /** 응답 HTTP 상태 */
    HttpStatus getStatus();

    /** 사용자에게 보여줄 기본 메시지 */
    String getMessage();
}
