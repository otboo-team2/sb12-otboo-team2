package com.otboo.recommendation.reference;

import com.otboo.pinterest.tag.StyleTag;
import java.util.List;

/**
 * 코디 참고 사진 한 장.
 *
 * @param pinUrl 원본 핀 주소. 화면에서 사진마다 반드시 이 주소로 연결한다 — 이미지를 복사하지 않고
 *               Pinterest 주소를 참조하는 조건으로 쓰는 것이라 출처 연결은 선택이 아니다
 * @param link   핀에 걸린 외부 링크(쇼핑몰 등). 없을 수 있다
 */
public record OutfitReferenceDto(
        String pinId,
        String imageUrl,
        String pinUrl,
        String link,
        String title,
        List<StyleTag> styles
) {
}
