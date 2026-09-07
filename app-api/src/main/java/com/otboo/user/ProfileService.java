package com.otboo.user;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.storage.ImageStorage;
import com.otboo.user.dto.ProfileDto;
import com.otboo.user.dto.ProfileUpdateRequest;
import com.otboo.user.entity.Profile;
import com.otboo.user.entity.User;
import com.otboo.user.exception.UserErrorCode;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.user.repository.UserRepository;
import com.otboo.weather.dto.WeatherApiLocation;
import com.otboo.weather.entity.WeatherRegion;
import com.otboo.weather.repository.WeatherRegionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final String IMAGE_DIRECTORY = "profiles";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final WeatherRegionRepository weatherRegionRepository;
    private final ImageStorage imageStorage;

    /** 다른 사람의 프로필도 볼 수 있다. 프론트가 {@code /profiles?userId=...} 로 이동한다. */
    @Transactional(readOnly = true)
    public ProfileDto find(UUID userId) {
        return profileRepository.findByUserId(userId)
                .map(ProfileDto::from)
                .orElseGet(() -> ProfileDto.empty(findUser(userId)));
    }

    /**
     * 프로필 수정. 호출 전에 본인 확인이 끝나 있어야 한다({@link UserController} 참고).
     *
     * <p>이름은 프로필이 아니라 계정({@code users.name})을 바꾼다. 이름을 두 곳에 두지 않기로 했다.
     */
    @Transactional
    public ProfileDto update(UUID userId, ProfileUpdateRequest request, MultipartFile image) {
        User user = findUser(userId);
        Profile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> profileRepository.save(Profile.createEmpty(user)));

        if (request.name() != null) {
            user.changeName(request.name());
        }
        profile.update(request.gender(), request.birthDate(), request.temperatureSensitivity());

        if (request.hasResolvableLocation()) {
            WeatherApiLocation location = request.location();
            profile.changeLocation(
                    toDecimal(location.latitude()),
                    toDecimal(location.longitude()),
                    resolveRegion(location),
                    Instant.now());
        }

        if (image != null && !image.isEmpty()) {
            String previousUrl = profile.getProfileImageUrl();
            profile.changeProfileImageUrl(imageStorage.store(image, IMAGE_DIRECTORY));
            // 새 이미지가 저장된 뒤에 지운다. 먼저 지우면 저장이 실패했을 때 둘 다 없어진다.
            imageStorage.delete(previousUrl);
        }

        return ProfileDto.from(profile);
    }

    /**
     * 격자 좌표로 지역을 찾고, 없으면 만든다.
     *
     * <p>지역은 사용자보다 훨씬 적고(전국 격자 단위) 여러 사람이 같은 격자를 공유하므로
     * 미리 다 넣어두는 대신 처음 쓰는 사람이 만들게 한다.
     *
     * <p>이미 있는 지역의 지명은 갱신하지 않는다. 같은 격자인데 지명만 달라지는 경우
     * (행정구역 개편·지오코딩 보정)가 있는데, {@code WeatherRegion} 에 갱신 수단이 없다.
     * KAN-29 리뷰로 요청해둔 상태다.
     */
    private WeatherRegion resolveRegion(WeatherApiLocation location) {
        return weatherRegionRepository.findByGridXAndGridY(location.x(), location.y())
                .orElseGet(() -> saveRegion(location));
    }

    private WeatherRegion saveRegion(WeatherApiLocation location) {
        try {
            return weatherRegionRepository.saveAndFlush(
                    WeatherRegion.create(location.x(), location.y(), location.locationNames()));
        } catch (DataIntegrityViolationException e) {
            // 두 사용자가 같은 격자를 동시에 처음 쓰면 유니크 제약에 걸린다. 먼저 넣은 쪽을 쓴다.
            return weatherRegionRepository.findByGridXAndGridY(location.x(), location.y())
                    .orElseThrow(() -> e);
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND)
                        .addDetail("userId", userId.toString()));
    }

    private static BigDecimal toDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
