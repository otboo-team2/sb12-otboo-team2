package com.otboo.clothes.search;

import java.util.UUID;

/** 추천 의상 검색 색인을 갱신해야 한다는 신호. */
public record ClothesIndexEvent(UUID clothesId, Operation operation) {

    public enum Operation {
        UPSERT,
        DELETE
    }

    public static ClothesIndexEvent upsert(UUID clothesId) {
        return new ClothesIndexEvent(clothesId, Operation.UPSERT);
    }

    public static ClothesIndexEvent delete(UUID clothesId) {
        return new ClothesIndexEvent(clothesId, Operation.DELETE);
    }
}
