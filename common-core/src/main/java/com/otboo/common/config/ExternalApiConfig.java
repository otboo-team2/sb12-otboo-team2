package com.otboo.common.config;

import com.otboo.common.http.ExternalApiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExternalApiProperties.class)
public class ExternalApiConfig {
}
