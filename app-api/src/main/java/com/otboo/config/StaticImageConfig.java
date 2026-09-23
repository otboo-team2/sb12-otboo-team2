package com.otboo.config;

import com.otboo.common.storage.StorageProperties;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 업로드된 이미지를 읽을 수 있게 정적 리소스로 연다.
 *
 * <p>{@code LocalImageStorage} 가 만든 URL({@code /images/...})과 경로가 같아야 한다.
 * S3 로 바꾸면 이 설정은 필요 없어진다.
 */
@Configuration
@RequiredArgsConstructor
public class StaticImageConfig implements WebMvcConfigurer {

    private final StorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.baseDir()).toAbsolutePath().normalize().toUri()
                .toString();
        registry.addResourceHandler(properties.baseUrl() + "/**")
                .addResourceLocations(location);
    }
}
