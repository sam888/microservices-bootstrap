package com.microservices.bootstrap.controller;

import com.microservices.bootstrap.exception.InternalException;
import com.microservices.bootstrap.service.DemoAuthService;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.auth.AuthRequestVO;
import com.microservices.bootstrap.vo.auth.AuthResponseVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * This controller mocks an external authentication API serving AuthenticationController
 *
 */
@RestController
@RequestMapping("demo-auth")
public class DemoAuthController {

   private final DemoAuthService demoAuthService;

   public DemoAuthController(DemoAuthService demoAuthService) {
      this.demoAuthService = demoAuthService;
   }


   @PostMapping("/token")
   public ResponseEntity<Mono<ApiResponseVO<AuthResponseVO>>> login(@RequestHeader("moduleCode") String moduleCode,
                                                                    @RequestBody AuthRequestVO requestVO) throws InternalException {
      Mono<ApiResponseVO<AuthResponseVO>> response = demoAuthService.login(requestVO, moduleCode);
      return new ResponseEntity<>(response, HttpStatus.OK);
   }
}
