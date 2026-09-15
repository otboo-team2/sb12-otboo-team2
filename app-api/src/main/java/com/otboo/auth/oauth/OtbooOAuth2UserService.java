package com.otboo.auth.oauth;

import com.otboo.common.exception.BusinessException;
import com.otboo.user.entity.OAuthProvider;
import com.otboo.user.entity.User;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * 제공자에게서 사용자 정보를 받은 직후, 우리 계정에 연결하는 지점.
 *
 * <p><b>성공 핸들러가 아니라 여기서 계정을 만든다.</b> 성공 핸들러는 인증이 끝난 뒤에 도는지라
 * 거기서 예외를 던지면 실패 핸들러로 가지 않고 500 이 나간다. 인증 단계인 여기서
 * {@link OAuth2AuthenticationException} 을 던져야 실패 핸들러가 받아 로그인 페이지로 돌려보낸다.
 */
@Service
@RequiredArgsConstructor
public class OtbooOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthLoginService oauthLoginService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(request);
        OAuthProvider provider =
                OAuthProvider.from(request.getClientRegistration().getRegistrationId());
        Map<String, Object> attributes = oauth2User.getAttributes();

        try {
            User user = oauthLoginService.login(provider, OAuthAttributes.of(provider, attributes));
            return new OtbooOAuth2User(user.getId(), attributes);
        } catch (BusinessException e) {
            // 사용자에게 보여줄 문구를 그대로 싣는다. 실패 핸들러가 쿼리 파라미터로 옮긴다.
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(e.getErrorCode().getCode(), e.getMessage(), null), e);
        }
    }
}
