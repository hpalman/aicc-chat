package aicc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("aicc.chat.mapper")
public class AiccChatApplication {
    public static void main(String[] args) {
        // Spring Boot 애플리케이션 실행 진입점
        SpringApplication.run(AiccChatApplication.class, args);
    }
}
