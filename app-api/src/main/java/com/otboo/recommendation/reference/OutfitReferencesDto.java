package com.otboo.recommendation.reference;

import com.otboo.pinterest.tag.SkyTag;
import com.otboo.pinterest.tag.TempBand;
import java.util.List;
import java.util.UUID;

/**
 * 날씨에 맞는 코디 참고 사진 목록.
 * <p>{@code tempBand} · {@code sky} 는 이 날씨를 어떻게 읽었는지다.
 * 화면이 "18°C · 흐림 기준" 처럼 조건을 보여줄 때 쓴다. 실제 검색은 {@code tempBand} 의 앞뒤 구간까지 넓혀서 한다.
 * <p>{@code references} 가 비어 있을 수 있다. Pinterest 동기화 전이거나 조건에 맞는 핀이 없는 경우다.
 */
public record OutfitReferencesDto(
        UUID weatherId,
        TempBand tempBand,
        SkyTag sky,
        List<OutfitReferenceDto> references
) {
}
