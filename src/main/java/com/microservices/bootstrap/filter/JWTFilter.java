package com.microservices.bootstrap.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@Order(2)
@Configuration
public class JWTFilter implements WebFilter {

    private static final String TOKEN_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer";

    private static final String[] NO_TOKEN = new String[] { "auth", "actuator", "favicon.ico", "demo" };

    @Value("${jwt.salt}")
    private String jwtSalt;

    @Autowired
    private ObjectMapper objectMapper;

    private static boolean stringContainsItemFromList(String inputStr) {
        return Arrays.stream( JWTFilter.NO_TOKEN ).parallel().anyMatch( inputStr::startsWith );
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value().substring(1);

        if ( !stringContainsItemFromList( path ) ) {
            String token = exchange.getRequest().getHeaders().getFirst(TOKEN_NAME);

            if (token == null) {
                return writeResponse(exchange, new BaseResponseVO("-1", "Authorisation header is missing", null), HttpStatus.UNAUTHORIZED);
            }

            if (token.startsWith(TOKEN_PREFIX)){
                token = token.substring(7);
            }
            try {
                AuthToken authToken = Authentication.decode( token, jwtSalt );
                log.info("Successful JWT validation! moduleCode={}, traderId={}", authToken.getModuleCode(), authToken.getTraderId() );
                String moduleCode = authToken.getModuleCode();
                long traderId = authToken.getTraderId();
                if (moduleCode != null) {
                    exchange.getAttributes().put("moduleCode", moduleCode);
                    exchange.getAttributes().put("traderId", traderId);
                } else {
                    return writeResponse(exchange, new BaseResponseVO("-1", "Invalid Authorisation token", "Module code is null"),
                            HttpStatus.BAD_REQUEST);
                }
            }catch (Exception e){
                return writeResponse( exchange, new BaseResponseVO("-1", "Invalid Authorisation token", e.getMessage()),
                        HttpStatus.BAD_REQUEST );
            }

        }

        return chain.filter(exchange);
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, BaseResponseVO body, HttpStatus status){
        Mono<DataBuffer> db = Mono.just(body).map( baseResponseVO -> {
            try {
                return objectMapper.writeValueAsBytes( baseResponseVO );
            } catch (JsonProcessingException e) {
                return e.getMessage().getBytes();
            }
        }).map( byateArray -> exchange.getResponse().bufferFactory().wrap( byateArray ));

        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().writeWith(db);
    }

}

@NoArgsConstructor
@AllArgsConstructor
@Data
class AuthToken{

    private String token;

    private String moduleCode;

    private Long traderId;

    private Instant expiration;

    private String userName;

    private String userType;

    public AuthToken(long userId, String userName, String userType, String moduleCode) {
        this.traderId = userId;
        this.userName = userName;
        this.userType = userType;
        this.moduleCode = moduleCode;
    }
}

@Slf4j
class Authentication {

    public final static String EXPIRATION = "exp";

    public static AuthToken decode(String token, String jwtSalt) throws JWTVerificationException {
        byte[] secret = Base64.decodeBase64( jwtSalt );

        Algorithm algorithm = Algorithm.HMAC256( secret );
        JWTVerifier verifier = JWT.require(algorithm)
            .acceptLeeway( 60 )
            .build();
        DecodedJWT jwt = verifier.verify( token );
        Map<String, Claim> claimMap = jwt.getClaims();

        return new AuthToken(
                claimMap.get("userId").as(Long.class),
                claimMap.get("userName").asString(),
                claimMap.get("userType").asString(),
                claimMap.get("moduleCode").asString());
    }

}
