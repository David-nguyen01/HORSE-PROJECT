package com.racehorse.media.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryConfiguration {

    @Bean
    Cloudinary cloudinary(CloudinaryProperties properties) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", emptyIfNull(properties.cloudName()),
                "api_key", emptyIfNull(properties.apiKey()),
                "api_secret", emptyIfNull(properties.apiSecret()),
                "secure", true));
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
