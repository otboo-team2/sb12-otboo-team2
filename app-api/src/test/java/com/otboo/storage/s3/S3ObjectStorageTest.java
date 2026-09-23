package com.otboo.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.common.exception.BusinessException;
import com.otboo.storage.s3.RemoteImageDownloader.RemoteImage;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageTest {

    @Mock
    S3Client s3Client;

    @Mock
    S3Presigner presigner;

    @Mock
    RemoteImageDownloader remoteImageDownloader;

    private S3ObjectStorage storage;

    @BeforeEach
    void setUp() {
        storage = new S3ObjectStorage(s3Client, presigner, remoteImageDownloader, properties());
    }

    @Test
    @DisplayName("업로드 결과는 공개 URL이 아니라 객체 키다")
    void storesMultipartAsObjectKey() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "model.jpg", "image/jpeg", new byte[]{1, 2, 3});

        String key = storage.store(file, "virtual-try-on/models");

        assertThat(key).startsWith("virtual-try-on/models/").endsWith(".jpg");
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("otboo-private");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("외부 이미지도 다운로드 후 객체 키로 저장한다")
    void storesRemoteImageAsObjectKey() {
        URI source = URI.create("https://cdn.example.com/result.png");
        given(remoteImageDownloader.download(source))
                .willReturn(new RemoteImage(new byte[]{4, 5}, "image/png; charset=binary"));

        String key = storage.storeFromUrl(source, "virtual-try-on/results");

        assertThat(key).startsWith("virtual-try-on/results/").endsWith(".png");
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("읽을 때만 제한된 수명의 Presigned URL을 만든다")
    void createsReadUrlOnDemand() throws Exception {
        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        given(presigned.url()).willReturn(new URL("https://otboo-private.s3.amazonaws.com/result.jpg"));
        given(presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(presigned);

        URI url = storage.readUrl("virtual-try-on/results/result.jpg");

        assertThat(url).hasScheme("https");
        ArgumentCaptor<GetObjectPresignRequest> request =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo("otboo-private");
        assertThat(request.getValue().getObjectRequest().key())
                .isEqualTo("virtual-try-on/results/result.jpg");
    }

    @Test
    @DisplayName("삭제는 버킷과 객체 키를 명시한다")
    void deletesByObjectKey() {
        storage.delete("virtual-try-on/results/result.jpg");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("otboo-private");
        assertThat(request.getValue().key()).isEqualTo("virtual-try-on/results/result.jpg");
    }

    @Test
    @DisplayName("허용 크기 초과와 안전하지 않은 디렉터리는 업로드 전에 거부한다")
    void rejectsInvalidUploads() {
        MockMultipartFile tooLarge = new MockMultipartFile(
                "image", "large.jpg", "image/jpeg", new byte[1025]);
        MockMultipartFile valid = new MockMultipartFile(
                "image", "model.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> storage.store(tooLarge, "models"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.store(valid, "../models"))
                .isInstanceOf(BusinessException.class);
    }

    private S3StorageProperties properties() {
        return new S3StorageProperties(
                true,
                "otboo-private",
                "ap-northeast-2",
                Duration.ofMinutes(10),
                1024,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }
}
