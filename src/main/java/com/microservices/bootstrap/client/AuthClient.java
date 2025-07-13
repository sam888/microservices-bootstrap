package com.microservices.bootstrap.client;

import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.auth.AuthRequestVO;
import com.microservices.bootstrap.vo.auth.AuthResponseVO;
import lombok.Getter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * @author samuel.huang
 * Created: 7-July-2025
 */
@Service
public class AuthClient extends BaseClient {

    private String authUrl;

    @Getter
    private WebClient authWebClient;
    
    public AuthClient(@Value("${app.auth-url}") String authUrl) {
        this.authUrl = authUrl;
        this.authWebClient = getWebClient( authUrl );
    }
    
    public Mono<ApiResponseVO<AuthResponseVO>> login(AuthRequestVO requestVO, String moduleCode){
        return getAuthWebClient().post().uri( "/demo-auth/token" ) // DemoAuthController will serve this request
            .header("moduleCode", moduleCode)
            .body( Mono.just( requestVO ), AuthRequestVO.class )
            .retrieve().bodyToMono( new ParameterizedTypeReference<>(){} );
    }

}
