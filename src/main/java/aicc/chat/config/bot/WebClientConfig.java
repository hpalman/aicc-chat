package aicc.chat.config.bot;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfig {
    
    private static final int MAX_CONNECTIONS = 100;
    private static final int PENDING_ACQUIRE_MAX_COUNT = 50;
    private static final Duration MAX_IDLE_TIME = Duration.ofSeconds(20);
    private static final Duration MAX_LIFE_TIME = Duration.ofMinutes(5);
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int WRITE_TIMEOUT_SECONDS = 60;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;
    
    @Bean
    public WebClient chatWebClient(WebClient.Builder builder) {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("chat-connection-pool")
            .maxConnections(MAX_CONNECTIONS)
            .pendingAcquireMaxCount(PENDING_ACQUIRE_MAX_COUNT)
            .maxIdleTime(MAX_IDLE_TIME)
            .maxLifeTime(MAX_LIFE_TIME)
            .evictInBackground(Duration.ofSeconds(30))
            .build();
        
        HttpClient httpClient = HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .responseTimeout(RESPONSE_TIMEOUT)
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)))
            .compress(true);
        
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
            .build();

        return builder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(exchangeStrategies)
            .build();
    }
}

