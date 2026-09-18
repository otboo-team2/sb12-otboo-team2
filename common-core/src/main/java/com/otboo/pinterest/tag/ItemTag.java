package com.otboo.pinterest.tag;

/** 코디의 핵심 아이템. 선택 태그다 — 없어도 핀은 유효하다. */
public enum ItemTag implements TagValue {

    KNIT("knit"),
    COAT("coat"),
    PADDING("padding"),
    JACKET("jacket"),
    SHIRT("shirt"),
    TEE("tee"),
    DRESS("dress"),
    SKIRT("skirt"),
    PANTS("pants"),
    DENIM("denim");

    private final String value;

    ItemTag(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
