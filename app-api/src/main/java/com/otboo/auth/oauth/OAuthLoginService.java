package com.otboo.auth.oauth;

import com.otboo.auth.exception.AuthErrorCode;
import com.otboo.common.exception.BusinessException;
import com.otboo.user.entity.OAuthProvider;
import com.otboo.user.entity.Profile;
import com.otboo.user.entity.User;
import com.otboo.user.entity.UserOAuthAccount;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.user.repository.UserOAuthAccountRepository;
import com.otboo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인으로 들어온 사람을 우리 계정에 연결한다.
 *
 * <h2>결정 1 — 이메일이 없으면 배달되지 않는 대체 주소로 가입시킨다</h2>
 * {@code users.email} 이 {@code NOT NULL UNIQUE} 인데 카카오 이메일은 비즈 앱이어야 받을 수 있고,
 * 이 앱은 비즈 앱 전환이 안 된다(9/30). 그래서 카카오는 닉네임만 받는다.
 *
 * <p>처음엔 "대체 주소가 DB 에 쌓이면 메일 발송이 걸림돌"이라며 이메일 없는 가입을 막았다. 그대로 두면
 * 카카오 로그인 자체가 불가능해서 뒤집었다. 대신 대체 주소는 {@code kakao_{id}@users.invalid} 로 만든다 —
 * {@code .invalid} 는 RFC 2606 예약 도메인이라 <b>어디로도 배달되지 않는다.</b> 실수로 발송해도 남의
 * 주소로 새지 않고, 주소만 봐도 "진짜 메일이 아님"이 드러난다. 이메일이 없으니 기존 계정 연결은 하지 않는다.
 *
 * <h2>결정 2 — 이미 가입된 이메일은 구글만 자동 연결한다</h2>
 * {@code me@gmail.com} 으로 일반 가입한 계정이 있는데 같은 이메일로 소셜 로그인을 하면?
 * 무조건 연결해주면 <b>제공자가 이메일 소유를 검증하지 않을 때 계정이 탈취된다.</b>
 * 공격자가 그 주소로 소셜 계정을 만들어 남의 계정에 들어간다.
 *
 * <p>구글은 {@code email_verified} 로 "이 주소가 실제로 이 사람 것"임을 보증한다.
 * 그 값이 참일 때만 연결하고, 보증이 불확실한 나머지는 거절한다.
 *
 * <h2>결정 3 — 소셜 계정은 비밀번호를 갖지 않는다</h2>
 * {@link User#createOAuth} 가 비밀번호 없이 만든다. {@code hasPassword()} 가 거짓이라
 * 일반 로그인 경로에서 자동으로 막힌다({@code AuthService.signIn}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    static final String PLACEHOLDER_EMAIL_DOMAIN = "users.invalid";

    private final UserRepository userRepository;
    private final UserOAuthAccountRepository oauthAccountRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public User login(OAuthProvider provider, OAuthAttributes attributes) {
        if (attributes.providerUserId() == null || attributes.providerUserId().isBlank()) {
            // 제공자 식별자가 없으면 같은 사람인지 다음 로그인 때 알 수 없다.
            throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ID_MISSING)
                    .addDetail("provider", provider.code());
        }

        User user = oauthAccountRepository
                .findByProviderAndProviderUserId(provider, attributes.providerUserId())
                .map(UserOAuthAccount::getUser)
                .orElseGet(() -> linkOrCreate(provider, attributes));

        if (user.isLocked()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        return user;
    }

    private User linkOrCreate(OAuthProvider provider, OAuthAttributes attributes) {
        String email = attributes.email();
        if (email == null || email.isBlank()) {
            return createUser(provider, attributes, placeholderEmail(provider, attributes.providerUserId()));
        }
        String normalizedEmail = email.trim().toLowerCase();

        return userRepository.findByEmail(normalizedEmail)
                .map(existing -> linkToExisting(provider, attributes, existing))
                .orElseGet(() -> createUser(provider, attributes, normalizedEmail));
    }

    /** 배달되지 않는 대체 주소. 제공자 식별자가 들어가 사람마다 다르다 */
    static String placeholderEmail(OAuthProvider provider, String providerUserId) {
        return provider.code() + "_" + providerUserId + "@" + PLACEHOLDER_EMAIL_DOMAIN;
    }

    /** 이미 있는 계정에 소셜을 붙인다. 제공자가 이메일 소유를 보증할 때만 허용한다. */
    private User linkToExisting(OAuthProvider provider, OAuthAttributes attributes, User existing) {
        if (!provider.equals(OAuthProvider.GOOGLE) || !attributes.emailVerified()) {
            log.info("oauth_link_rejected provider={} reason=unverified_email",
                    provider.code());
            throw new BusinessException(AuthErrorCode.OAUTH_EMAIL_ALREADY_REGISTERED)
                    .addDetail("provider", provider.code());
        }
        oauthAccountRepository.save(
                UserOAuthAccount.link(existing, provider, attributes.providerUserId()));
        log.info("oauth_linked provider={} userId={}", provider.code(), existing.getId());
        return existing;
    }

    private User createUser(OAuthProvider provider, OAuthAttributes attributes, String email) {
        User user = userRepository.saveAndFlush(
                User.createOAuth(email, attributes.displayName()));
        // 일반 가입과 같다. 조회 시점에 만들면 GET 이 쓰기를 하게 된다.
        profileRepository.save(Profile.createEmpty(user));
        oauthAccountRepository.save(
                UserOAuthAccount.link(user, provider, attributes.providerUserId()));

        log.info("oauth_signed_up provider={} userId={}", provider.code(), user.getId());
        return user;
    }
}
