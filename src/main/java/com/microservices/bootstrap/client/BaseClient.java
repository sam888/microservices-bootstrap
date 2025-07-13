package com.microservices.bootstrap.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microservices.bootstrap.enums.Constants;
import com.microservices.bootstrap.exception.InternalException;
import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;


@Slf4j
@Service
public class BaseClient {

    @Value("${app.member-url}")
    private String memberUrl;
    
    public static final String MDC_KEY = Constants.MDC_KEY.value();
    public static final String X_REQUEST_ID = Constants.X_REQUEST_ID.value();

    protected WebClient getWebClient(String url) {
        return getWebClient( url, false);
    }

    protected WebClient getWebClient(String url, boolean useCustomErrorFilter) {
        WebClient.Builder builder = WebClient.builder()
                .filter( logRequest() );     // Log request

        // Flexibility for custom error handling filter in subclass of BaseClient if required, e.g. fixed error code for certain URL
        if ( ! useCustomErrorFilter ) {
            builder = builder.filter( errorHandlingFilter() );  // Apply default error handler
        }

        return builder.defaultHeader( X_REQUEST_ID, MDC.get( MDC_KEY ))
            .exchangeStrategies( getExchangeStrategies() )
            .clientConnector(
                new ReactorClientHttpConnector(
                    HttpClient.create() // DO not use HttpClient.newConnection() as we want to re-use TCP connection from pool
                        .compress( true )
                        .option( ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000 )   // 10 sec connection timeout
                        .responseTimeout( Duration.ofSeconds( 15 ) )              // 15 sec response timeout
                )
            )
            .baseUrl( url )
            .build();
    }

    protected ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Calling API: {} {}", clientRequest.method(), clientRequest.url());
            
            // Uncomment to log header for debugging purpose only
            // clientRequest.headers().forEach((name, values) -> values.forEach(value -> log.info("{}={}", name, value)));
            return Mono.just(clientRequest);
        });
    }

    protected ExchangeStrategies getExchangeStrategies(){
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return ExchangeStrategies
                .builder()
                .codecs(clientDefaultCodecsConfigurer -> {
                    clientDefaultCodecsConfigurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                    clientDefaultCodecsConfigurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
                }).build();
    }

    protected static ExchangeFilterFunction errorHandlingFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            HttpStatusCode httpStatusCode = clientResponse.statusCode();
            if ( httpStatusCode.is2xxSuccessful() ) {
                return Mono.just(clientResponse);
            }else {
                return clientResponse.bodyToMono( BaseResponseVO.class )
                        .flatMap(errorBody -> Mono.error(new InternalException( errorBody )));
            }
        });
    }

    
}
