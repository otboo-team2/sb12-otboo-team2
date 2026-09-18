package com.otboo.pinterest.repository;

import com.otboo.pinterest.entity.PinterestPin;
import com.otboo.pinterest.tag.GenderTag;
import com.otboo.pinterest.tag.SkyTag;
import com.otboo.pinterest.tag.StyleTag;
import com.otboo.pinterest.tag.TempBand;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PinterestPinRepository extends JpaRepository<PinterestPin, UUID> {

    Optional<PinterestPin> findByPinId(String pinId);

    /** 동기화 한 번에 받은 핀들의 기존 행을 한 번에 가져온다. 핀마다 조회하면 N+1 이 된다. */
    List<PinterestPin> findAllByPinIdIn(Collection<String> pinIds);

    /**
     * 추천에 쓸 핀. 동기화한 이 테이블에서만 찾는다 — Pinterest 를 부르지 않는다.
     *
     * <h2>조건을 넓히는 것은 호출자가 한다</h2>
     * 넘긴 값에 해당하는 핀만 나온다. 후보가 모자랄 때 어디까지 양보할지는 추천 쪽 정책이라
     * 여기서 정하지 않는다. 넓히려면 넓힌 집합을 넘긴다.
     * <ul>
     *   <li>기온 — {@link TempBand#adjacent()} 로 앞뒤 구간까지</li>
     *   <li>날씨 — {@code EnumSet.allOf(SkyTag.class)} 로 날씨 무관</li>
     *   <li>성별 — {@code Set.of(MEN, UNISEX)} 처럼 UNISEX 를 함께. 넣지 않으면 남성 핀만 나온다</li>
     * </ul>
     *
     * <p>{@code styles} 가 비어 있으면 스타일로 걸러내지 않는다. 스타일은 핀마다 여러 개라
     * 자식 테이블에 있고, 넘긴 스타일 중 <b>하나라도</b> 붙은 핀이 나온다.
     *
     * <p>{@link com.otboo.pinterest.tag.TagStatus#TAGGED} 만 나온다. 태그가 없거나 틀린 핀은
     * 기온·날씨를 모르는 핀이라 추천에 쓸 수 없다.
     *
     * @param limit 가져올 최대 개수. 0 이하면 빈 목록
     * @return 최근에 동기화한 핀 우선. 조건 집합 중 하나라도 비어 있으면 빈 목록
     */
    default List<PinterestPin> findTaggedFor(Collection<TempBand> tempBands, Collection<SkyTag> skies,
            Collection<GenderTag> genders, Collection<StyleTag> styles, int limit) {
        if (tempBands.isEmpty() || skies.isEmpty() || genders.isEmpty() || limit <= 0) {
            return List.of();
        }
        Pageable page = PageRequest.ofSize(limit);
        if (styles.isEmpty()) {
            return findTagged(tempBands, skies, genders, page);
        }
        // 태그는 enum 이름으로 저장된다(MINIMAL). description 표기(minimal)가 아니다.
        Set<String> styleNames = styles.stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
        return findTaggedWithStyles(tempBands, skies, genders, styleNames, page);
    }

    /** {@link #findTaggedFor} 가 부른다. 직접 부르지 말고 그쪽을 쓴다 — 빈 집합 처리가 거기에 있다. */
    @Query("""
            select pin from PinterestPin pin
            where pin.tagStatus = com.otboo.pinterest.tag.TagStatus.TAGGED
              and pin.tempBand in :tempBands
              and pin.sky in :skies
              and pin.gender in :genders
            order by pin.syncedAt desc, pin.pinId desc
            """)
    List<PinterestPin> findTagged(
            @Param("tempBands") Collection<TempBand> tempBands,
            @Param("skies") Collection<SkyTag> skies,
            @Param("genders") Collection<GenderTag> genders,
            Pageable pageable);

    /**
     * {@link #findTaggedFor} 가 부른다. 직접 부르지 말고 그쪽을 쓴다.
     *
     * <p>JOIN 이 아니라 {@code exists} 다. 스타일이 두 개 붙은 핀이 JOIN 에서는 두 줄로 나와
     * {@code limit} 이 핀 수가 아니라 줄 수를 세게 된다. {@code distinct} 로 덮으면 정렬·페이징이
     * 중복 제거 뒤에 걸려 더 헷갈린다.
     */
    @Query("""
            select pin from PinterestPin pin
            where pin.tagStatus = com.otboo.pinterest.tag.TagStatus.TAGGED
              and pin.tempBand in :tempBands
              and pin.sky in :skies
              and pin.gender in :genders
              and exists (select 1 from PinterestPinTag tag
                          where tag.pin = pin
                            and tag.tagType = com.otboo.pinterest.entity.PinTagType.STYLE
                            and tag.tagValue in :styleNames)
            order by pin.syncedAt desc, pin.pinId desc
            """)
    List<PinterestPin> findTaggedWithStyles(
            @Param("tempBands") Collection<TempBand> tempBands,
            @Param("skies") Collection<SkyTag> skies,
            @Param("genders") Collection<GenderTag> genders,
            @Param("styleNames") Collection<String> styleNames,
            Pageable pageable);
}
