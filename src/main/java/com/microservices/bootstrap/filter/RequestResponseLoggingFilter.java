package com.microservices.bootstrap.filter;

import com.microservices.bootstrap.enums.Constants;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
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
                    log.info( "Running time {}:{}:{} (M:SS:sss)", duration.toMinutes(), duration.toSeconds(), duration.toMillisPart() );
                    MDC.clear();
                })
                .contextWrite( Context.of(Constants.MDC_KEY.value(), requestId) );
    }

    public static String getDataByDataBuffer(DataBuffer dataBuffer)  {
        StringBuilder stringBuilder = new StringBuilder();

        // DataBuffer.readableByteBuffers() will allow reading of byte data from request/response multiple times
        dataBuffer.readableByteBuffers().forEachRemaining( byteBuffer -> {
            byte[] bytes = new byte[ byteBuffer.remaining() ];
            byteBuffer.get( bytes );
            stringBuilder.append( new String(bytes, StandardCharsets.UTF_8) );
        });
        return stringBuilder.toString();
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
            String body = RequestResponseLoggingFilter.getDataByDataBuffer( dataBuffer );
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
        Mono<DataBuffer> buffer = Mono.from(body);

        return super.writeWith( buffer.doOnNext( dataBuffer -> {
            String response = RequestResponseLoggingFilter.getDataByDataBuffer( dataBuffer );
            log.info("Response payload of {}: {}", requestPath, response);
        }));
    }
}
