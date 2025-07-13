package com.microservices.bootstrap.controller;

import com.microservices.bootstrap.dto.ApiListResponseDTO;
import com.microservices.bootstrap.dto.RateResponseDTO;
import com.microservices.bootstrap.service.ExchangeRateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * This controller is purely for testing exchange rates from ExchangeRateService
 */
@RestController
@RequestMapping("demo-rates")
public class DemoExchangeRateController {

   private final ExchangeRateService exchangeRateService;


   public DemoExchangeRateController(ExchangeRateService exchangeRateService) {
      this.exchangeRateService = exchangeRateService;
   }

   @GetMapping( "/all/usd" )
   public ResponseEntity<Mono<ApiListResponseDTO<List<RateResponseDTO>>>> getAllUsdRates() {
      Mono<ApiListResponseDTO<List<RateResponseDTO>>> apiResponseDtoMono = exchangeRateService.getAllUsdRates();
      return new ResponseEntity<>(apiResponseDtoMono, HttpStatus.OK);
   }
}
