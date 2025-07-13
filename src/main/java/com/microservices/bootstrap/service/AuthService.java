package com.microservices.bootstrap.service;


import com.microservices.bootstrap.client.AuthClient;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.auth.AuthRequestVO;
import com.microservices.bootstrap.vo.auth.AuthResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Slf4j
@Service
public class AuthService {

    private final AuthClient authClient;


    public AuthService(AuthClient client) {
        this.authClient = client;
    }

    public Mono<ApiResponseVO<AuthResponseVO>> login(AuthRequestVO authRequestVO, String moduleCode){
        return authClient.login(authRequestVO, moduleCode);
    }

}
