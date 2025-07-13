package com.microservices.bootstrap.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.microservices.bootstrap.exception.InternalException;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.auth.AuthRequestVO;
import com.microservices.bootstrap.vo.auth.AuthResponseVO;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Service
public class DemoAuthService {

   @Value("${jwt.salt}")
   private String jwtSalt;

   // Hardcoded username and password for demo
   private String acceptedUsername = "autumnWind";
   private String acceptedPassword = "HaGqklkGHL4567";

   public Mono<ApiResponseVO<AuthResponseVO>> login(AuthRequestVO authRequestVO, String moduleCode) throws InternalException {
      String username = authRequestVO.getUsername();
      String password = authRequestVO.getPassword();

      if ( !"DEMO".equals( moduleCode ) ) {
         return Mono.error(new InternalException( "-3", "Invalid moduleCode", null ));
      }

      if ( !acceptedUsername.equals( username ) && !acceptedPassword.equals( password ) ) {
         String errorMessage = "Wrong username or password!";
         return Mono.error(new InternalException( "-2", errorMessage, errorMessage ));
      }

      LocalDateTime nowLocalDateTime = LocalDateTime.now();
      LocalDateTime expiryLocalDateTime = nowLocalDateTime.plusHours(1);

      Date nowDate  = Date.from( nowLocalDateTime.atZone( ZoneId.systemDefault() ).toInstant() );
      Date expiryDate  = Date.from( expiryLocalDateTime.atZone( ZoneId.systemDefault() ).toInstant() );

      try {
         byte[] secret = Base64.decodeBase64( jwtSalt );
         Algorithm algorithmT = Algorithm.HMAC256(secret);

         JWTCreator.Builder builder = JWT.create();
         builder.withIssuer( "Demo Ltd" )
                 .withJWTId( UUID.randomUUID().toString() )
                 .withIssuedAt( nowDate )
                 .withExpiresAt( expiryDate )
                 .withClaim("moduleCode", moduleCode)
                 .withClaim("userId",  "75678117")
                 .withClaim("userName", authRequestVO.getUsername() )
                 .withClaim("userType", "3" );
         String token = builder.sign(algorithmT);

         AuthResponseVO authResponseVO = new AuthResponseVO();
         authResponseVO.setExpiration( expiryLocalDateTime );
         authResponseVO.setToken(token);
         authResponseVO.success();

         return Mono.just( new ApiResponseVO<>( authResponseVO ) );
      } catch (JWTCreationException exception) {
         throw new InternalException("-5", "fail.issue.token", "fail.issue.token");
      }
   }

}
