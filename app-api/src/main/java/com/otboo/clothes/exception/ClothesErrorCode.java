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
    INVALID_ATTRIBUTE_DEFINITION_SORT(
            "CLOTHES_005", HttpStatus.BAD_REQUEST, "지원하지 않는 의상 속성 정렬 기준입니다."),
    DUPLICATE_CLOTHES_ATTRIBUTE(
            "CLOTHES_006", HttpStatus.BAD_REQUEST, "같은 속성 정의는 한 의상에 중복할 수 없습니다."),
    EMPTY_CLOTHES_UPDATE(
            "CLOTHES_007", HttpStatus.BAD_REQUEST, "변경할 의상 정보가 없습니다."),
    INVALID_PRODUCT_URL(
            "CLOTHES_008", HttpStatus.BAD_REQUEST, "상품 URL이 올바르지 않습니다."),
    UNSAFE_PRODUCT_URL(
            "CLOTHES_009", HttpStatus.BAD_REQUEST, "접근할 수 없는 상품 URL입니다."),
    PRODUCT_DATA_NOT_FOUND(
            "CLOTHES_010", HttpStatus.BAD_REQUEST, "상품 정보를 찾을 수 없습니다."),
    INVALID_REMOTE_IMAGE(
            "CLOTHES_011", HttpStatus.BAD_REQUEST, "상품 이미지가 올바르지 않습니다."),
    IMAGE_SOURCE_CONFLICT(
            "CLOTHES_012", HttpStatus.BAD_REQUEST, "이미지 파일과 원격 이미지를 함께 사용할 수 없습니다."),
    REMOTE_RESOURCE_TOO_LARGE(
            "CLOTHES_013", HttpStatus.PAYLOAD_TOO_LARGE, "외부 상품 데이터 크기가 허용 범위를 초과했습니다."),

    NOT_OWNER(
            "CLOTHES_100", HttpStatus.FORBIDDEN, "본인의 의상만 등록하거나 수정할 수 있습니다."),

    ATTRIBUTE_DEFINITION_NOT_FOUND(
            "CLOTHES_201", HttpStatus.NOT_FOUND, "의상 속성 정의를 찾을 수 없습니다."),
    CLOTHES_NOT_FOUND(
            "CLOTHES_202", HttpStatus.NOT_FOUND, "의상을 찾을 수 없습니다."),

    DUPLICATE_ATTRIBUTE_DEFINITION_NAME(
            "CLOTHES_301", HttpStatus.CONFLICT, "이미 존재하는 의상 속성 이름입니다."),
    ATTRIBUTE_DEFINITION_IN_USE(
            "CLOTHES_302", HttpStatus.CONFLICT, "사용 중인 의상 속성 정의는 삭제할 수 없습니다."),
    SELECTABLE_VALUE_IN_USE(
            "CLOTHES_303", HttpStatus.CONFLICT, "사용 중인 의상 속성 선택값은 제거할 수 없습니다."),
    CLOTHES_IN_USE(
            "CLOTHES_304", HttpStatus.CONFLICT, "다른 기능에서 사용 중인 의상은 삭제할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
