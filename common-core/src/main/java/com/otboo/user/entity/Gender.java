package com.otboo.user.entity;

/** 프로필 성별. DB CHECK 제약(ck_profiles_gender)과 값이 같아야 한다. */
public enum Gender {

    MALE,
    FEMALE,
    OTHER
}
