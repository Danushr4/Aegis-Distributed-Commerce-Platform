package com.aegis.orderservice.config;

import com.aegis.orderservice.filter.CorrelationIdFilter;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Global WebClient with connect and response timeouts; propagates X-Correlation-Id to downstream services.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(
            @Value("${app.dependency.connectTimeoutMs:2000}") int connectTimeoutMs,
            @Value("${app.dependency.responseTimeoutMs:5000}") int responseTimeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(responseTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(responseTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter((request, next) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null && !correlationId.isBlank()) {
                        request = ClientRequest.from(request)
                                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
                                .build();
                    }
                    return next.exchange(request);
                })
                .build();
    }
}
