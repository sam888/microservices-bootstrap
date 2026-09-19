package com.microservices.bootstrap.client;

import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.auth.AuthRequestVO;
import com.microservices.bootstrap.vo.auth.AuthResponseVO;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * @author samuel.huang
 * Created: 7-July-2025
 */
@Service
public class AuthClient {

    private final WebClient authWebClient;
    
    public AuthClient(WebClient authWebClient) {
        this.authWebClient = authWebClient;
    }
    
    public Mono<ApiResponseVO<AuthResponseVO>> login(AuthRequestVO requestVO, String moduleCode){
        return authWebClient.post().uri( "/demo-auth/token" ) // DemoAuthController will serve this request
            .header("moduleCode", moduleCode)
            .body( Mono.just( requestVO ), AuthRequestVO.class )
            .retrieve().bodyToMono( new ParameterizedTypeReference<>(){} );
    }

}
