package aicc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AiccChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiccChatApplication.class, args);
    }
}
