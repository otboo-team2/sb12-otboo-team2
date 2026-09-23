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

    /** 외부 URL 의 이미지를 내려받아 우리 저장소에 다시 저장하고, 새로 발급한 키를 반환한다. */
    String storeFromUrl(String remoteUrl, String directory);

    /**
     * 저장된 키로부터 클라이언트(브라우저)가 지금 바로 열어볼 수 있는 URL 을 만든다.
     * 로컬 구현체는 키 자체가 이미 접근 가능한 상대경로라 그대로 반환하지만,
     * S3 구현체는 여기서 presigned URL 을 발급해야 한다.
     */
    String resolveUrl(String key);

    /**
     * 저장된 키를 우리 서버에 접근할 수 없는 외부 API(FASHN)가 읽을 수 있는 형태로 변환한다.
     * 로컬 구현체는 파일을 직접 읽어 base64 data URI 로 인코딩해서 넘긴다
     * 이미 외부에서 접근 가능한 URL(S3 presigned URL, 기본 모델 이미지 등)이면 그대로 반환한다.
     */
    String readAsDataUri(String urlOrKey);
}
