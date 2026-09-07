package com.otboo.clothes.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ClothesErrorCode implements ErrorCode {

    INVALID_ATTRIBUTE_NAME(
            "CLOTHES_001", HttpStatus.BAD_REQUEST, "속성 이름이 올바르지 않습니다."),
    INVALID_SELECTABLE_VALUE(
            "CLOTHES_002", HttpStatus.BAD_REQUEST, "선택값이 올바르지 않습니다."),
    DUPLICATE_SELECTABLE_VALUE_IN_REQUEST(
            "CLOTHES_003", HttpStatus.BAD_REQUEST, "선택값은 중복될 수 없습니다."),
    EMPTY_ATTRIBUTE_UPDATE(
            "CLOTHES_004", HttpStatus.BAD_REQUEST, "변경할 속성 값이 없습니다."),

    ATTRIBUTE_DEFINITION_NOT_FOUND(
            "CLOTHES_201", HttpStatus.NOT_FOUND, "의상 속성 정의를 찾을 수 없습니다."),

    DUPLICATE_ATTRIBUTE_DEFINITION_NAME(
            "CLOTHES_301", HttpStatus.CONFLICT, "이미 존재하는 의상 속성 이름입니다."),
    ATTRIBUTE_DEFINITION_IN_USE(
            "CLOTHES_302", HttpStatus.CONFLICT, "사용 중인 의상 속성 정의는 삭제할 수 없습니다."),
    SELECTABLE_VALUE_IN_USE(
            "CLOTHES_303", HttpStatus.CONFLICT, "사용 중인 의상 속성 선택값은 제거할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
