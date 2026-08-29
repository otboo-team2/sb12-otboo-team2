package com.otboo.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * 모든 도메인 예외의 부모.
 *
 * <p>각 파트는 이 클래스를 상속해 도메인 예외를 만들거나, 그대로 던져도 된다.
 * <pre>
 *   throw new BusinessException(ClothesErrorCode.NOT_FOUND)
 *           .addDetail("clothesId", clothesId.toString());
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> details = new LinkedHashMap<>();

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /** 디버깅에 필요한 부가 정보를 담는다. 비밀번호·토큰 등 민감정보는 넣지 말 것. */
    public BusinessException addDetail(String key, String value) {
        this.details.put(key, value);
        return this;
    }
}
