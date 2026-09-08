package com.otboo.user;

import com.otboo.common.exception.BusinessException;
import com.otboo.user.dto.UserCreateRequest;
import com.otboo.user.dto.UserDto;
import com.otboo.user.entity.User;
import com.otboo.user.exception.UserErrorCode;
import com.otboo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDto create(UserCreateRequest request) {
        String email = request.email();

        // 먼저 확인해서 흔한 경우에 친절한 메시지를 준다.
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(UserErrorCode.EMAIL_DUPLICATED).addDetail("email", email);
        }

        User user = User.create(email, passwordEncoder.encode(request.password()), request.name());
        try {
            return UserDto.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 다른 요청이 같은 이메일로 가입할 수 있다.
            // 실제로 중복을 막는 건 유니크 제약이고, 여기서 그 결과를 같은 에러로 바꿔준다.
            throw new BusinessException(UserErrorCode.EMAIL_DUPLICATED, e).addDetail("email", email);
        }
    }
}
