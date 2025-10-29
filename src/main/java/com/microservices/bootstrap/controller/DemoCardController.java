package com.microservices.bootstrap.controller;

import com.microservices.bootstrap.service.DemoCardService;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.CardDetailsResponseVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * This controller mocks an external Transaction API serving CardController
 *
 */
@RestController
@RequestMapping("demo-cards")
public class DemoCardController {

   private final DemoCardService demoCardService;



   public DemoCardController(DemoCardService demoCardService) {
      this.demoCardService = demoCardService;
   }

   @GetMapping("/{cardNumber}")
   @ResponseStatus( HttpStatus.OK )
   public Mono<ApiResponseVO<CardDetailsResponseVO>> getCardDetails(
           @RequestHeader("moduleCode") String moduleCode,
           @PathVariable String cardNumber) {
      return demoCardService.getCardDetails(moduleCode, cardNumber);
   }
}
