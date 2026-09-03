package com.otboo.user.entity;

import com.otboo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, length = 320)
    private String email;

    /** 소셜 로그인 전용 계정은 비밀번호가 없다. 반드시 인코딩된 값만 들어온다. */
    @Column(length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    private boolean locked;

    private User(String email, String encodedPassword, String name, Role role) {
        this.email = email;
        this.password = encodedPassword;
        this.name = name;
        this.role = role;
        this.locked = false;
    }

    public static User create(String email, String encodedPassword, String name) {
        return new User(email, encodedPassword, name, Role.USER);
    }

    /** 소셜 로그인으로 처음 들어온 계정. 비밀번호가 없다. */
    public static User createOAuth(String email, String name) {
        return new User(email, null, name, Role.USER);
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void changeLocked(boolean locked) {
        this.locked = locked;
    }

    /** 비밀번호가 없는 계정은 일반 로그인을 할 수 없다. */
    public boolean hasPassword() {
        return password != null;
    }
}
