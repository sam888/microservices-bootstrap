package com.microservices.bootstrap.controller;

import com.microservices.bootstrap.exception.InternalException;
import com.microservices.bootstrap.service.CardService;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.CardDetailsResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * @author samuel.huang
 * Created: 13-June-2025
 */
@Slf4j
@RestController
@RequestMapping("/cards")
public class CardController {

   private final CardService cardService;

   public CardController(CardService cardService) {
      this.cardService = cardService;
   }

   @GetMapping("/{cardNumber}")
   public ResponseEntity<Mono<ApiResponseVO<CardDetailsResponseVO>>> getCardDetails(
           @RequestHeader("moduleCode") String moduleCode,
           @PathVariable String cardNumber) throws InternalException {
      Mono<ApiResponseVO<CardDetailsResponseVO>> response = cardService.getCardDetails(moduleCode, cardNumber);
      return new ResponseEntity<>(response, HttpStatus.OK);
   }
}
