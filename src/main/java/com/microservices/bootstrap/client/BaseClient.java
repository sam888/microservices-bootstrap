package com.microservices.bootstrap.client;

import com.microservices.bootstrap.enums.Constants;
import com.microservices.bootstrap.exception.InternalException;
import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;


@Slf4j
@Service
public class BaseClient {
    
    public static final String MDC_KEY = Constants.MDC_KEY.value();
    public static final String X_REQUEST_ID = Constants.X_REQUEST_ID.value();

   // ── Pool configuration constants ─────────────────────────────────────────
   private static final int    MAX_CONNECTIONS           = 100;  // per backend host
   private static final int    PENDING_ACQUIRE_MAX_COUNT = 50;   // queue before immediate rejection
   private static final int    PENDING_ACQUIRE_TIMEOUT_S = 5;    // sec before queued request fails
   private static final int    MAX_IDLE_TIME_S           = 30;   // sec before idle connection released
   private static final int    MAX_LIFE_TIME_M           = 5;    // min before connection recycled
   private static final int    EVICT_INTERVAL_S          = 30;   // sec between background eviction runs
   private static final int    CONNECT_TIMEOUT_MS        = 3_000; // ms to establish TCP connection
   private static final int    RESPONSE_TIMEOUT_S        = 15;   // sec for backend to start responding

   @Autowired
   private ExchangeStrategies exchangeStrategies;

    protected WebClient getWebClient(String url) {
        return getWebClient( url, false);
    }

    protected WebClient getWebClient(String url, boolean useCustomErrorFilter) {

       ConnectionProvider provider = ConnectionProvider.builder( url )
               .maxConnections( MAX_CONNECTIONS )
               .pendingAcquireMaxCount( PENDING_ACQUIRE_MAX_COUNT )
               .pendingAcquireTimeout( Duration.ofSeconds( PENDING_ACQUIRE_TIMEOUT_S ) )
               .maxIdleTime( Duration.ofSeconds( MAX_IDLE_TIME_S ) )
               .maxLifeTime( Duration.ofMinutes( MAX_LIFE_TIME_M ) )
               .evictInBackground( Duration.ofSeconds( EVICT_INTERVAL_S ))
               .metrics( true )    // exposes pool metrics to Micrometer / JvmDiagnosticsLogger
               .build();

       // HttpClient created per provider — lightweight wrapper, safe to create per WebClient.
       // The expensive resource (TCP connection pool) lives in the provider above, not here.
       HttpClient httpClient = HttpClient.create(provider)
               .compress(true)
               .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
               .responseTimeout(Duration.ofSeconds( RESPONSE_TIMEOUT_S ));
       // Note: ReadTimeoutHandler / WriteTimeoutHandler not needed here.
       // responseTimeout covers the full request/response cycle for small JSON payloads.
       // ReadTimeoutHandler only adds value for large streamed responses (e.g. file downloads).

        WebClient.Builder builder = WebClient.builder()
                .filter( logRequest() );     // Log request

        // Flexibility for custom error handling filter in subclass of BaseClient if required, e.g. fixed error code for certain URL
        if ( ! useCustomErrorFilter ) {
            builder = builder.filter( errorHandlingFilter() );  // Apply default error handler
        }

       return builder
            .defaultHeader( X_REQUEST_ID, MDC.get(  MDC_KEY ) )
            .exchangeStrategies( exchangeStrategies )
            .clientConnector( new ReactorClientHttpConnector( httpClient ) )
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
