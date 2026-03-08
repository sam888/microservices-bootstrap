package com.microservices.bootstrap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

/**
 * @author samuel.huang
 * Created: 05-March-2026
 *
 */
@Slf4j
@Configuration
public class JsonConfig {

    /**
     * 
     * @param objectMapper the default ObjectMapper bean autoconfigured by Spring Boot 3.x with sensible defaults (including 
     * JavaTimeModule, Kotlin support, etc.). Note this objectMapper already has 
     *   objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
     */
    @Bean
    protected ExchangeStrategies getExchangeStrategies(ObjectMapper objectMapper) {
        return ExchangeStrategies.builder()
            .codecs(config -> {
                config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024); // 2MB
                config.defaultCodecs().jackson2JsonEncoder(
                        new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                config.defaultCodecs().jackson2JsonDecoder(
                        new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
            }).build();
    }
}
