package com.otboo.user;

import com.otboo.common.event.UserRoleChangedEvent;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.user.UserSpecifications.UserSortField;
import com.otboo.user.dto.ChangePasswordRequest;
import com.otboo.user.dto.UserCreateRequest;
import com.otboo.user.dto.UserDto;
import com.otboo.user.dto.UserLockUpdateRequest;
import com.otboo.user.dto.UserRoleUpdateRequest;
import com.otboo.user.dto.UserSearchCondition;
import com.otboo.user.entity.Profile;
import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import com.otboo.user.exception.UserErrorCode;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserDto create(UserCreateRequest request) {
        String email = request.email();

        // 먼저 확인해서 흔한 경우에 친절한 메시지를 준다.
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(UserErrorCode.EMAIL_DUPLICATED).addDetail("email", email);
        }

        User user = User.create(email, passwordEncoder.encode(request.password()), request.name());
        try {
            User saved = userRepository.saveAndFlush(user);
            // 프로필을 여기서 같이 만든다. 조회 시점에 만들면 GET 이 쓰기를 하게 된다.
            profileRepository.save(Profile.createEmpty(saved));
            return UserDto.from(saved);
        } catch (DataIntegrityViolationException e) {
            // 위 확인과 저장 사이에 다른 요청이 같은 이메일로 가입할 수 있다.
            // 실제로 중복을 막는 건 유니크 제약이고, 여기서 그 결과를 같은 에러로 바꿔준다.
            throw new BusinessException(UserErrorCode.EMAIL_DUPLICATED, e).addDetail("email", email);
        }
    }

    /**
     * 계정 목록(관리자). 커서 페이지네이션.
     *
     * <p>{@code totalCount} 는 필터에만 걸린 전체 건수다. 커서 조건을 넣고 세면
     * 페이지를 넘길 때마다 총 개수가 줄어든다.
     */
    @Transactional(readOnly = true)
    public CursorResponse<UserDto> findAll(CursorRequest request, UserSearchCondition condition) {
        UserSortField sortField = parseSortField(request.sortBy());

        Specification<User> filter = UserSpecifications.filter(condition);
        Sort sort = sortOf(sortField, request.sortDirection());

        List<User> fetched = userRepository.findAll(
                filter.and(UserSpecifications.afterCursor(request, sortField)),
                PageRequest.of(0, request.fetchSize(), sort)).getContent();

        List<UserDto> data = fetched.stream().map(UserDto::from).toList();
        long totalCount = userRepository.count(filter);

        return CursorResponse.of(data, request, totalCount,
                dto -> sortField == UserSortField.EMAIL ? dto.email() : dto.createdAt(),
                UserDto::id);
    }

    @Transactional
    public UserDto updateRole(UUID userId, UserRoleUpdateRequest request, UUID actorId) {
        User user = findUser(userId);
        // 마지막 관리자가 스스로 권한을 내리면 관리자 화면에 아무도 못 들어간다.
        if (user.getId().equals(actorId) && request.role() != Role.ADMIN) {
            throw new BusinessException(UserErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        Role previous = user.getRole();
        user.changeRole(request.role());

        if (previous != request.role()) {
            // 권한이 바뀌면 알림을 보낸다. 소비자는 알림 파트에서 붙인다.
            eventPublisher.publishEvent(UserRoleChangedEvent.of(user.getId(), request.role()));
            // 토큰에 role 이 들어 있어 재로그인 전까지 이전 권한이 그대로 먹는다.
            refreshTokenRepository.deleteAllByUser(user);
        }
        return UserDto.from(user);
    }

    @Transactional
    public UserDto updateLock(UUID userId, UserLockUpdateRequest request, UUID actorId) {
        User user = findUser(userId);
        if (user.getId().equals(actorId) && Boolean.TRUE.equals(request.locked())) {
            throw new BusinessException(UserErrorCode.CANNOT_LOCK_SELF);
        }

        user.changeLocked(request.locked());
        if (Boolean.TRUE.equals(request.locked())) {
            // 잠갔는데 기존 세션이 살아 있으면 잠근 의미가 없다.
            refreshTokenRepository.deleteAllByUser(user);
        }
        return UserDto.from(user);
    }

    /**
     * 비밀번호 변경. 본인 확인은 컨트롤러에서 끝난다.
     *
     * <p>변경하면 기존 리프레시 토큰을 전부 지운다. 비밀번호를 바꾸는 이유 중 하나가
     * "누가 내 계정을 쓰는 것 같다"인데, 남의 세션이 그대로 살아 있으면 소용이 없다.
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        user.changePassword(passwordEncoder.encode(request.password()));
        refreshTokenRepository.deleteAllByUser(user);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND)
                        .addDetail("userId", userId.toString()));
    }

    private static Sort sortOf(UserSortField sortField, SortDirection direction) {
        Sort.Direction jpaDirection = direction.isAscending()
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        // id 를 마지막에 붙이지 않으면 정렬 키가 같은 행의 순서가 흔들려 커서가 무의미해진다.
        return Sort.by(jpaDirection, sortField.attribute()).and(Sort.by(jpaDirection, "id"));
    }

    private static UserSortField parseSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return UserSortField.CREATED_AT;
        }
        return switch (sortBy) {
            case "email" -> UserSortField.EMAIL;
            case "createdAt" -> UserSortField.CREATED_AT;
            // 아무 문자열이나 받으면 매핑되지 않은 속성으로 쿼리가 터진다.
            default -> throw new BusinessException(UserErrorCode.INVALID_SORT_BY)
                    .addDetail("sortBy", sortBy)
                    .addDetail("allowed", "email, createdAt");
        };
    }
}
