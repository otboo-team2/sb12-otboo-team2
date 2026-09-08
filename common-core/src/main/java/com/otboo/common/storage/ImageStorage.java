package com.otboo.common.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드된 이미지를 저장하고 접근 URL 을 돌려준다.
 *
 * <p>프로필 이미지 · 의상 이미지 · 가상 피팅 결과가 모두 이 계약을 쓴다.
 * 각 파트는 구현체를 몰라도 되고, 나중에 S3 로 바꿔도 도메인 코드는 그대로다.
 *
 * <p><b>지금 구현체는 로컬 디스크 하나뿐이다.</b> 배포에서 인스턴스를 여러 개 띄우면
 * 각자 다른 디스크에 저장돼 이미지가 반쪽만 보인다. S3 전환은 별도 티켓이다.
 */
public interface ImageStorage {

    /**
     * @param file      업로드 파일. 비어 있으면 안 된다
     * @param directory 용도별 하위 경로. 예: {@code "profiles"}, {@code "clothes"}
     * @return 저장된 이미지의 접근 URL
     */
    String store(MultipartFile file, String directory);

    /** 이전 이미지를 지운다. 없는 URL 이 들어와도 예외를 던지지 않는다. */
    void delete(String url);
}
