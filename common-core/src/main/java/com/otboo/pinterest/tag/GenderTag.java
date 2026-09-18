package com.otboo.pinterest.tag;

public enum GenderTag implements TagValue {

    MEN("men"),
    WOMEN("women"),
    UNISEX("unisex");

    private final String value;

    GenderTag(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
