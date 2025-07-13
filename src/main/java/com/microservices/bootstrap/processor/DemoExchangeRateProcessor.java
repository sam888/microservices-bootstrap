package com.microservices.bootstrap.processor;

import com.microservices.bootstrap.dto.RateResponseDTO;
import com.microservices.bootstrap.service.ExchangeRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DemoExchangeRateProcessor {

   private final ExchangeRateService exchangeRateService;

   public DemoExchangeRateProcessor(ExchangeRateService exchangeRateService) {
      this.exchangeRateService = exchangeRateService;
   }

   /**
    * Simulate updating an external demo app's exchange rate after extracting the best USD rate from ASB & Westpac
    *
    * @return
    */
   // @Scheduled(initialDelayString = "${scheduler.exchange-rate.initial-delay}", fixedRateString = "${scheduler.exchange-rate.fixed-rate}")
   public void updateDemoExchangeRate() {
      exchangeRateService.getAllUsdRates()
        .doOnNext(data -> {
           List<RateResponseDTO> rateResponseDTOList = data.getData();
           rateResponseDTOList.forEach(rateResponseDTO -> {
              log.info("Extracted exchange rate: " + rateResponseDTO);
           });
        })
        .doOnError(e -> log.error("Error retrieving exchange rates", e))
        .subscribe();
   }

}
