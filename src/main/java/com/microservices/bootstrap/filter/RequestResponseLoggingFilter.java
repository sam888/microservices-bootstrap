package com.microservices.bootstrap.filter;

import com.microservices.bootstrap.enums.Constants;
import static com.microservices.bootstrap.filter.RequestResponseLoggingFilter.getDataByDataBuffer;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;


@Slf4j
@Configuration
@Order(1)
public class RequestResponseLoggingFilter implements WebFilter {

    private static final String API_PREFIX = "demo";
    private static final int MAX_LOG_CHARS = 512;
    private static final String[] NO_LOGGING_URI = new String[] {"/favicon.ico", "/actuator"};

    @Override
    public Mono<Void> filter(ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        Instant startInstant = Instant.now();
        ServerHttpRequest request = serverWebExchange.getRequest();
        ServerHttpResponse response = serverWebExchange.getResponse();

        if ( containsNoLoggingUri( request.getURI().getPath() ) ) {
            return webFilterChain.filter( serverWebExchange );
        }

        String requestPath = request.getPath().toString();
        String requestId = getRequestId();
        
        MDC.put( Constants.MDC_KEY.value(), requestId );
        response.getHeaders().add( Constants.X_REQUEST_ID.value(), requestId ); // add response header for every request
        log.info( "Received request: HTTP {} {}", request.getMethod(), request.getURI() );

        RequestLoggingDecorator requestLoggingDecorator = new RequestLoggingDecorator( request );
        ResponseLoggingDecorator responseLoggingDecorator = new ResponseLoggingDecorator( response,
                requestPath );

        return webFilterChain.filter( serverWebExchange.mutate().request( requestLoggingDecorator)
                        .response( responseLoggingDecorator).build() )
                /*.doOnSuccess(a -> {
                
                }).doOnEach(a -> {
                
                })*/
                .doFinally( a -> {
                    // Log performance of each request
                    Instant endInstant = Instant.now();
                    Duration duration = Duration.between( startInstant , endInstant );
                    if ( duration.toMillis() < 5_000 ) {
                        log.info( "HTTP {} {} returned in {} ms", request.getMethod(), request.getURI(), duration.toMillis() );
                    } else {
                        log.warn("Warning: More than 5 sec. HTTP {} {} returned in {} ms", request.getMethod(),
                                request.getURI(), duration.toMillis());
                    }
                    MDC.clear();
                })
                .contextWrite( Context.of(Constants.MDC_KEY.value(), requestId) );
    }

    public static String getDataByDataBuffer(DataBuffer dataBuffer)  {
        StringBuilder stringBuilder = new StringBuilder();

        try (DataBuffer.ByteBufferIterator it = dataBuffer.readableByteBuffers()) {
            while ( it.hasNext() ) {
                stringBuilder.append( StandardCharsets.UTF_8.decode( it.next() ) );
                if (stringBuilder.length() >= MAX_LOG_CHARS) break;
            }
        }

        return stringBuilder.length() > MAX_LOG_CHARS
                ? stringBuilder.substring(0, MAX_LOG_CHARS) + "... [truncated]"
                : stringBuilder.toString();
    }

    private String getRequestId() {
        return API_PREFIX + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean containsNoLoggingUri(String inputStr) {
        return Arrays.stream( NO_LOGGING_URI ).parallel().anyMatch( inputStr::contains );
    }
}

@Slf4j
class RequestLoggingDecorator extends ServerHttpRequestDecorator {

    public RequestLoggingDecorator(ServerHttpRequest delegate) {
        super(delegate);
    }

    @Override
    public Flux<DataBuffer> getBody() {
        return super.getBody().doOnNext(dataBuffer -> {
            String body = getDataByDataBuffer( dataBuffer );
            log.info("Request payload of {}: {}", getDelegate().getPath(), body);
        });
    }
}

@Slf4j
class ResponseLoggingDecorator extends ServerHttpResponseDecorator {

    private final String requestPath;

    public ResponseLoggingDecorator(ServerHttpResponse delegate, String requestPath) {
        super(delegate);
        this.requestPath = requestPath;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        return DataBufferUtils.join(body)
                .doOnNext(buf -> log.info("Response payload of {}: {}", requestPath, getDataByDataBuffer( buf ) ) )
                .flatMap( buf -> super.writeWith( Mono.just( buf ) ) );
    }
}
