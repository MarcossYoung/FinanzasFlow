package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                Path uploadDir = Paths.get("uploads");
                String uploadPath = uploadDir.toFile().getAbsolutePath();

                registry.addResourceHandler("/uploads/**")
                        .addResourceLocations("file:" + uploadPath + "/");
            }

            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                // Redirect all non-API routes to index.html for React Router
                registry.addViewController("/{spring:\\w+}")
                        .setViewName("forward:/index.html");
                registry.addViewController("/*/{spring:\\w+}")
                        .setViewName("forward:/index.html");
                registry.addViewController("/{spring:\\w+}/*{spring:?!(\\.js|\\.css)$}")
                        .setViewName("forward:/index.html");
            }
        };
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
