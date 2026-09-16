package com.otboo.user.preference;

import com.otboo.clothes.repository.ClothesAttributeSelectableValueRepository;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final ClothesAttributeSelectableValueRepository selectableValueRepository;

    @Transactional(readOnly = true)
    public List<UserPreferenceDto> find(AuthPrincipal me, UUID userId) {
        checkOwner(me, userId);
        return preferenceRepository.findAllByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(UserPreferenceDto::from)
                .toList();
    }

    @Transactional
    public List<UserPreferenceDto> replace(
            AuthPrincipal me, UUID userId, UserPreferenceUpdateRequest request) {
        checkOwner(me, userId);
        List<UUID> ids = request.selectableValueIds();
        if (ids.size() != new HashSet<>(ids).size()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE)
                    .addDetail("selectableValueIds", "중복된 선호 선택값이 있습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        var values = selectableValueRepository.findAllById(ids);
        if (values.size() != ids.size()) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
                    .addDetail("selectableValueIds", "존재하지 않는 선택값이 있습니다.");
        }

        preferenceRepository.deleteAllByUserId(userId);
        preferenceRepository.flush();
        var saved = preferenceRepository.saveAll(
                values.stream().map(value -> UserPreference.create(user, value)).toList());
        return saved.stream().map(UserPreferenceDto::from).toList();
    }

    private static void checkOwner(AuthPrincipal me, UUID userId) {
        if (!me.userId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
