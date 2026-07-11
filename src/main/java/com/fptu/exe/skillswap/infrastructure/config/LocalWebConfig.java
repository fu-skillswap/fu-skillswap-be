package com.fptu.exe.skillswap.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
@Profile("local")
public class LocalWebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public LocalWebConfig(@Value("${application.upload.dir:${java.io.tmpdir}/skillswap-storage}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path path = Paths.get(uploadDir).toAbsolutePath().normalize();
        String resourceLocation = "file:" + path.toString() + "/";
        
        registry.addResourceHandler("/uploads/storage/**")
                .addResourceLocations(resourceLocation);
                
        log.info("LocalWebConfig: Exposing {} at /uploads/storage/**", resourceLocation);
    }
}
