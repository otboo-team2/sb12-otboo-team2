package com.otboo.user.entity;

public enum Role {
    USER,
    ADMIN;

    /** Spring Security 는 권한 이름에 ROLE_ 접두사를 기대한다. */
    public String authority() {
        return "ROLE_" + name();
    }
}
