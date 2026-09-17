package com.otboo.storage.s3;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** S3 기반을 명시적으로 활성화한 환경에서만 AWS 클라이언트와 저장 컴포넌트를 만든다. */
@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
@ConditionalOnProperty(prefix = "otboo.storage.s3", name = "enabled", havingValue = "true")
public class S3StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client privateS3Client(S3StorageProperties properties) {
        properties.requireConfigured();
        return S3Client.builder()
                .region(Region.of(properties.region()))
                // 로컬 프로필, 환경변수, ECS Task Role 순으로 찾는다. Access Key를 설정에 넣지 않는다.
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner privateS3Presigner(S3StorageProperties properties) {
        properties.requireConfigured();
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    HttpClient s3RemoteImageHttpClient(S3StorageProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    RemoteImageDownloader remoteImageDownloader(
            HttpClient s3RemoteImageHttpClient,
            S3StorageProperties properties
    ) {
        return new RemoteImageDownloader(s3RemoteImageHttpClient, properties);
    }

    @Bean
    S3ObjectStorage s3ObjectStorage(
            S3Client privateS3Client,
            S3Presigner privateS3Presigner,
            RemoteImageDownloader remoteImageDownloader,
            S3StorageProperties properties
    ) {
        return new S3ObjectStorage(
                privateS3Client,
                privateS3Presigner,
                remoteImageDownloader,
                properties);
    }
}
