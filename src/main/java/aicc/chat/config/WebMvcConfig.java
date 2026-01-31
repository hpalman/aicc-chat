package aicc.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    // 정적 리소스(frontend) 경로 매핑 설정
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        log.info("■ addResourceHandlers. frontend path to url");
        // 프로젝트 루트의 'frontend' 폴더를 '/frontend/**' URL로 매핑
        // file:./frontend/ 는 현재 실행 경로(프로젝트 루트)의 frontend 폴더를 의미함
        registry.addResourceHandler("/frontend/**")
                .addResourceLocations("file:./frontend/");
    }

    @Override
    // CORS 허용 범위와 메서드 설정
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        log.info("■ addCorsMappings");
        registry.addMapping("/**")
                .allowedOrigins("http://localhost", "http://localhost:80", "http://localhost:3000", "http://ipv4.fiddler:28070")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

