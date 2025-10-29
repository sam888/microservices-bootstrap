package com.microservices.bootstrap.controller;

import com.microservices.bootstrap.service.AuthService;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.auth.AuthRequestVO;
import com.microservices.bootstrap.vo.auth.AuthResponseVO;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * @author samuel.huang
 * @Date 07/07/2025
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/token")
    @ResponseStatus( HttpStatus.OK )
    public Mono<ApiResponseVO<AuthResponseVO>> login(@RequestHeader("moduleCode") String moduleCode,
                                                     @Validated @RequestBody AuthRequestVO requestVO){
        return authService.login(requestVO, moduleCode);
    }
}
