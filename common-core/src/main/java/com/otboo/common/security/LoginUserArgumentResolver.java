package com.otboo.common.security;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUser} 가 붙은 파라미터에 인증 주체를 넣는다.
 *
 * <p>요청 본문에 authorId 가 들어 있어도 여기서 넣어주는 값만 쓰면 위조가 불가능하다.
 */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && AuthPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        LoginUser annotation = parameter.getParameterAnnotation(LoginUser.class);
        if (annotation != null && !annotation.required()) {
            return null;
        }
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
}
