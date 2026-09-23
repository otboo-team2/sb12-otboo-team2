package com.otboo.user.entity;

import com.otboo.common.entity.BaseEntity;
import com.otboo.weather.entity.WeatherRegion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사용자 프로필. 계정({@link User})과 1:1 이다.
 *
 * <p><b>이름은 여기에 두지 않는다.</b> {@code users.name} 하나뿐이고 {@code ProfileDto.name} 은
 * 계정에서 가져다 채운다. 두 곳에 두면 한쪽만 바뀌는 사고가 난다.
 *
 * <p>모든 항목이 선택이라 컬럼이 전부 nullable 이다. 가입 직후에는 빈 프로필이 만들어진다.
 */
@Entity
@Getter
@Table(name = "profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** DECIMAL(10,6). double 로 받으면 스키마 검증에서 타입이 어긋난다. */
    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    /** 격자 좌표와 지명은 여기서 조인해 꺼낸다. 프로필에 복사해두지 않는다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private WeatherRegion region;

    /** 위치를 마지막으로 갱신한 시각. 오래된 위치로 날씨를 보여주는 것을 판단할 때 쓴다. */
    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    /** 1(추위에 민감) ~ 5(더위에 민감). DB 는 TINYINT 다. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "temperature_sensitivity")
    private Integer temperatureSensitivity;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    private Profile(User user) {
        this.user = user;
    }

    /** 가입 직후 만들어지는 빈 프로필. */
    public static Profile createEmpty(User user) {
        return new Profile(user);
    }

    /**
     * 부분 수정. {@code null} 인 항목은 <b>"바꾸지 않음"</b>으로 본다.
     *
     * <p>PATCH 라서 프론트가 바꾼 항목만 보낸다. null 을 "비우기"로 해석하면
     * 이름만 고쳐도 성별·생일이 다 날아간다.
     */
    public void update(Gender gender, LocalDate birthDate, Integer temperatureSensitivity) {
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (temperatureSensitivity != null) {
            this.temperatureSensitivity = temperatureSensitivity;
        }
    }

    public void changeLocation(BigDecimal latitude, BigDecimal longitude, WeatherRegion region,
            Instant updatedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.region = region;
        this.locationUpdatedAt = updatedAt;
    }

    public void changeProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
