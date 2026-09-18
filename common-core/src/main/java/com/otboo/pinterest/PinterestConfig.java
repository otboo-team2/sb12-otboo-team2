package com.otboo.pinterest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PinterestProperties.class)
public class PinterestConfig {
}
