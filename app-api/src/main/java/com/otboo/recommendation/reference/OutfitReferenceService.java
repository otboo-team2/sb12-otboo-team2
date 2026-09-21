package com.otboo.recommendation.reference;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.pinterest.entity.PinTagType;
import com.otboo.pinterest.entity.PinterestPin;
import com.otboo.pinterest.repository.PinterestPinRepository;
import com.otboo.pinterest.tag.GenderTag;
import com.otboo.pinterest.tag.SkyTag;
import com.otboo.pinterest.tag.StyleTag;
import com.otboo.pinterest.tag.TempBand;
import com.otboo.user.entity.Gender;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.SkyStatus;
import com.otboo.weather.entity.Weather;
import com.otboo.weather.repository.WeatherRepository;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 날씨에 맞는 코디 참고 사진을 찾는다.
 *
 * <h2>Pinterest 를 부르지 않는다</h2>
 * 동기화 배치가 채워둔 {@code pinterest_pins} 만 읽는다. 그래서 Pinterest 토큰이 없어도 동작하고,
 * 동기화 전에는 빈 목록을 돌려준다. 토큰이 나와 배치가 돌면 이 코드는 그대로 사진을 돌려준다.
 * /토큰 1-2일 내 생성 예정으로 승인 되는대로 전달 예정.
 *
 * <h2>검색 조건을 넓히는 기준</h2>
 * 핀은 큐레이터가 손으로 태그를 단 수십~수백 장이라 조건을 정확히 맞추면 금방 비어 버린다.
 * <ul>
 *   <li>기온 — 현재 기온의 구간과 그 앞뒤 구간. "18도" 에 17-19 만 보면 20-22 코디를 놓친다</li>
 *   <li>성별 — 공용 핀은 항상 포함한다. 프로필에 성별이 없으면 거르지 않는다</li>
 * </ul>
 * 날씨는 넓히지 않는다. 비 오는 날 맑은 날 코디를 보여주면 추천이 틀린 것이 된다.
 */
@Service
@RequiredArgsConstructor
public class OutfitReferenceService {

    static final int DEFAULT_LIMIT = 12;
    static final int MAX_LIMIT = 30;

    private static final String PIN_URL = "https://www.pinterest.com/pin/%s/";

    private final WeatherRepository weatherRepository;
    private final ProfileRepository profileRepository;
    private final PinterestPinRepository pinRepository;

    @Transactional(readOnly = true)
    public OutfitReferencesDto find(UUID userId, UUID weatherId, Collection<StyleTag> styles, Integer limit) {
        Weather weather = weatherRepository.findById(weatherId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
                        .addDetail("weatherId", weatherId.toString()));

        TempBand band = TempBand.fromCelsius(weather.getTemperatureCurrent().doubleValue());
        SkyTag sky = skyOf(weather.getSkyStatus(), weather.getPrecipitationType());
        Set<GenderTag> genders = gendersFor(profileRepository.findByUserId(userId)
                .map(profile -> profile.getGender())
                .orElse(null));

        List<OutfitReferenceDto> references = pinRepository.findTaggedFor(
                        band.adjacent(), Set.of(sky), genders,
                        styles == null ? List.of() : styles, clampLimit(limit))
                .stream()
                .map(OutfitReferenceService::toDto)
                .toList();

        return new OutfitReferencesDto(weatherId, band, sky, references);
    }

    /**
     * 강수가 하늘 상태보다 먼저다. 흐리면서 비가 오는 날은 "흐림" 이 아니라 "비" 코디가 필요하다.
     * 진눈깨비는 눈으로 본다 — 둘 다 방수·보온이 필요하고, 비 코디로는 춥다.
     */
    static SkyTag skyOf(SkyStatus skyStatus, PrecipitationType precipitation) {
        if (precipitation != null) {
            switch (precipitation) {
                case RAIN, SHOWER -> {
                    return SkyTag.RAIN;
                }
                case SNOW, RAIN_SNOW -> {
                    return SkyTag.SNOW;
                }
                case NONE -> {
                }
            }
        }
        return skyStatus == SkyStatus.CLEAR ? SkyTag.CLEAR : SkyTag.CLOUDY;
    }

    /** 공용 핀은 누구에게나 맞는다. 성별을 모르면 거르지 않는다. */
    static Set<GenderTag> gendersFor(Gender gender) {
        if (gender == null) {
            return EnumSet.allOf(GenderTag.class);
        }
        return switch (gender) {
            case MALE -> EnumSet.of(GenderTag.MEN, GenderTag.UNISEX);
            case FEMALE -> EnumSet.of(GenderTag.WOMEN, GenderTag.UNISEX);
            case OTHER -> EnumSet.allOf(GenderTag.class);
        };
    }

    static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.clamp(limit, 1, MAX_LIMIT);
    }

    private static OutfitReferenceDto toDto(PinterestPin pin) {
        List<StyleTag> styles = pin.getTags().stream()
                .filter(tag -> tag.getTagType() == PinTagType.STYLE)
                .map(tag -> styleOf(tag.getTagValue()))
                .filter(style -> style != null)
                .sorted()
                .toList();
        return new OutfitReferenceDto(
                pin.getPinId(),
                pin.getImageUrl(),
                PIN_URL.formatted(pin.getPinId()),
                pin.getLink(),
                pin.getTitle(),
                styles);
    }

    /** 어휘에서 빠진 값이 DB 에 남아 있어도 응답 전체가 깨지지 않게 건너뛴다. */
    private static StyleTag styleOf(String value) {
        return Arrays.stream(StyleTag.values())
                .filter(style -> style.name().equals(value))
                .findFirst()
                .orElse(null);
    }
}
