package com.otboo.clothes.dto;

/** 구매 링크 정보 중 자동 추출하지 못한 항목과 사유다. */
public record ClothesExtractionFailureDto(String field, String reason) {
}
