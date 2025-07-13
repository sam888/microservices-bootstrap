package com.microservices.bootstrap.service;

import com.microservices.bootstrap.exception.InternalException;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.CardDetailsResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
public class DemoCardService {

   // Hardcoded card number for demo purpose
   private String acceptedCardNumber = "60800838383";


   public Mono<ApiResponseVO<CardDetailsResponseVO>> getCardDetails(String moduleCode, String cardNumber) {

      if ( !"DEMO".equals( moduleCode ) ) {
         return Mono.error(new InternalException( "-3", "Invalid moduleCode", null ));
      }

      if ( !acceptedCardNumber.equals( cardNumber ) ) {
         return Mono.error(new InternalException( "-4", "No such card exists", null ));
      }

      CardDetailsResponseVO cardDetailsResponseVO = new CardDetailsResponseVO();
      cardDetailsResponseVO.setCardNumber( acceptedCardNumber );
      cardDetailsResponseVO.setBalance( BigDecimal.ZERO );
      cardDetailsResponseVO.setTraderId( Long.valueOf( 45678 ) );
      cardDetailsResponseVO.setExpiryDate( LocalDate.now().plusYears( 2 )  );
      cardDetailsResponseVO.success();

      return Mono.just( new ApiResponseVO<>( cardDetailsResponseVO ) );
   }
}
