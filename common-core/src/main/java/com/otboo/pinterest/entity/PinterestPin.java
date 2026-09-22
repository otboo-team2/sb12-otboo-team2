package com.otboo.pinterest.entity;

import com.otboo.common.entity.BaseEntity;
import com.otboo.pinterest.tag.GenderTag;
import com.otboo.pinterest.tag.OutfitTagParseResult;
import com.otboo.pinterest.tag.OutfitTags;
import com.otboo.pinterest.tag.SkyTag;
import com.otboo.pinterest.tag.TagStatus;
import com.otboo.pinterest.tag.TempBand;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * 동기화한 Pinterest 핀 한 개. 코디 추천 검색은 Pinterest 가 아니라 이 테이블에서 한다.
 *
 * <h2>이미지는 저장하지 않는다</h2>
 * Pinterest 이미지 주소만 들고 있다. 파일을 우리 저장소로 복사하지 않는다 —
 * Pinterest 자료를 어디까지 보관해도 되는지는 개발자 가이드라인을 따라야 하고,
 * 주소만 참조하는 쪽이 가장 보수적이다.
 *
 * <h2>description 원문을 그대로 들고 있는다</h2>
 * 태그 어휘가 바뀌면 Pinterest 를 다시 부르지 않고 이 컬럼만 다시 파싱하면 된다.
 */
@Entity
@Getter
@Table(name = "pinterest_pins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PinterestPin extends BaseEntity {

    static final int MAX_TAG_ERRORS_LENGTH = 1000;

    @Column(name = "pin_id", nullable = false, updatable = false, length = 64)
    private String pinId;

    @Column(name = "board_id", nullable = false, length = 64)
    private String boardId;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "link", length = 2048)
    private String link;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "description", length = 800)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_status", nullable = false, length = 16)
    private TagStatus tagStatus;

    /** 파싱 오류를 줄바꿈으로 이은 값. 큐레이터에게 무엇을 고칠지 알려주는 용도다. */
    @Column(name = "tag_errors", length = MAX_TAG_ERRORS_LENGTH)
    private String tagErrors;

    @Enumerated(EnumType.STRING)
    @Column(name = "temp_band", length = 16)
    private TempBand tempBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "sky", length = 16)
    private SkyTag sky;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 16)
    private GenderTag gender;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    /**
     * 핀 여러 개의 태그를 한 번의 IN 쿼리로 읽는다. 없으면 핀마다 쿼리가 한 번씩 더 나간다(N+1).
     *
     * <p>fetch join 대신 쓰는 이유 — 추천 조회는 limit 이 걸린 페이지 조회라, 컬렉션을 fetch join 하면
     * Hibernate 가 전체를 메모리로 올린 뒤 자른다. 100 은 동기화 배치의 한 페이지 크기와 맞춘 값이다.
     */
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "pin", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<PinterestPinTag> tags = new ArrayList<>();

    public static PinterestPin create(String pinId, String boardId, String imageUrl, String link,
                                      String title, String description,
                                      OutfitTagParseResult parsed, Instant syncedAt) {
        PinterestPin pin = new PinterestPin();
        pin.pinId = pinId;
        pin.refresh(boardId, imageUrl, link, title, description, parsed, syncedAt);
        return pin;
    }

    /** 같은 핀을 다시 동기화했을 때 부른다. 큐레이터가 description 을 고쳤을 수 있다. */
    public void refresh(String boardId, String imageUrl, String link, String title,
                        String description, OutfitTagParseResult parsed, Instant syncedAt) {
        this.boardId = boardId;
        this.imageUrl = imageUrl;
        this.link = link;
        this.title = title;
        this.description = description;
        this.syncedAt = syncedAt;

        OutfitTags parsedTags = parsed.tags();
        this.tagStatus = parsed.status();
        this.tagErrors = joinErrors(parsed.errors());
        this.tempBand = parsedTags.temp();
        this.sky = parsedTags.sky();
        this.gender = parsedTags.gender();
        replaceTags(parsedTags);
    }

    public List<PinterestPinTag> getTags() {
        return Collections.unmodifiableList(tags);
    }

    /**
     * 달라진 태그만 지우고 더한다.
     *
     * <p>전부 지우고 다시 넣으면 안 된다. Hibernate 는 flush 할 때 INSERT 를 DELETE 보다 먼저 보내서,
     * 그대로 남아야 할 {@code (핀, STYLE, MINIMAL)} 이 지워지기도 전에 다시 들어가며
     * 유니크 제약 {@code uk_pinterest_pin_tags} 에 걸린다.
     */
    private void replaceTags(OutfitTags parsedTags) {
        Set<TagKey> wanted = new HashSet<>();
        parsedTags.styles().forEach(style -> wanted.add(new TagKey(PinTagType.STYLE, style.name())));
        parsedTags.items().forEach(item -> wanted.add(new TagKey(PinTagType.ITEM, item.name())));

        tags.removeIf(tag -> !wanted.contains(new TagKey(tag.getTagType(), tag.getTagValue())));
        for (TagKey key : wanted) {
            boolean exists = tags.stream().anyMatch(tag -> tag.matches(key.type(), key.value()));
            if (!exists) {
                tags.add(new PinterestPinTag(this, key.type(), key.value()));
            }
        }
    }

    private static String joinErrors(List<String> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        String joined = String.join("\n", errors);
        return joined.length() <= MAX_TAG_ERRORS_LENGTH
                ? joined
                : joined.substring(0, MAX_TAG_ERRORS_LENGTH - 1) + "…";
    }

    private record TagKey(PinTagType type, String value) {
    }
}
