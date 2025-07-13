package com.microservices.bootstrap.service;

import com.microservices.bootstrap.client.WestpacClient;
import com.microservices.bootstrap.dto.ApiListResponseDTO;
import com.microservices.bootstrap.dto.RateResponseDTO;
import com.microservices.bootstrap.dto.WestpacRateResultDTO;
import com.microservices.bootstrap.enums.ErrorNotifierType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ExchangeRateService {

   private final WestpacClient westpacClient;

   @Value("${api.environment}")
   private String environment;

   public ExchangeRateService(WestpacClient westpacClient) {
      this.westpacClient = westpacClient;
   }

   // Uncomment this block and comment above constructor for sending error notification email after proper
   // spring.mail.host is configured in application-uat.yml
   /**
      private final EmailService emailService;

      public ExchangeRateService(WestpacClient westpacClient, EmailService emailService) {
         this.westpacClient = westpacClient;
         this.emailService = emailService;
      }
   **/

   /**
    * Collect USD exchange rates from all banks.
    * @return
    */
   public Mono<ApiListResponseDTO<List<RateResponseDTO>>> getAllUsdRates() {
      Duration timeoutDuration = Duration.ofSeconds( 7 );

      // Uncomment .doOnError(...) to send error notification email after 3 failed attempts to access Westpac API
      Mono<RateResponseDTO> westpacRateMono = getUsdRateByWestpac().timeout( timeoutDuration )
              .retry( 2 ) // A total of 3 attempts will be made before sending error notification email below
              // .doOnError(throwable -> sendErrorNotificationEmail( "Westpac", "USD", ErrorNotifierType.WESTPAC, throwable ))
              .onErrorResume( e -> Mono.just( getErrorRateResponse( "Westpac", "USD") ) );

      // Collect all results, replacing errors with fallback responses so processing can keep going
      return Flux.merge( westpacRateMono  ).collectList()
              .flatMap( rateResponseDtoList -> Mono.just( new ApiListResponseDTO<>( rateResponseDtoList ) ));
   }

   public Mono<RateResponseDTO> getUsdRateByWestpac() {
      return westpacClient.getUsdRateByWestpac().flatMap( westpacRateResponseDTO -> {
         WestpacRateResultDTO result = westpacRateResponseDTO.getInternationalPayment();
         if ( result == null ) {
            return Mono.just( getErrorRateResponse( "Westpac", "USD" ));
         }

         String rate = result.getRate();
         RateResponseDTO rateResponseDTO = getSuccessfulRateResponse("Westpac", "USD", rate );
         log.info("Westpac rate: " + rateResponseDTO);
         return Mono.just( rateResponseDTO );
      });
   }

   private void sendErrorNotificationEmail(String source, String currency,
                                           ErrorNotifierType errorNotifierType,
                                           Throwable throwable) {
      Exception exception = (throwable instanceof Exception) ? (Exception)throwable : new RuntimeException( throwable );
      String subject = environment + " Demo Exchange Rate API: Error fetching exchange rate from " + source + " for " + currency;
      String message = subject;

      // Uncomment this line for sending error notification email after spring.mail.host is configured in application-uat.yml
      // emailService.sendErrorNotificationEmail(subject, message, errorNotifierType, exception);
      log.error("Error fetching rate from {} for {}: {}", source, currency, exception.getMessage(), exception );
   }

   public RateResponseDTO getErrorRateResponse(String provider, String currencyCode) {
      String errorMessage = "Failed to retrieve exchange rate!";
      RateResponseDTO rateResponseDTO = initRateResponseDTO(provider, currencyCode, errorMessage);
      rateResponseDTO.error( errorMessage );
      return rateResponseDTO;
   }

   public RateResponseDTO getSuccessfulRateResponse(String provider, String currencyCode, String rate) {
      RateResponseDTO rateResponseDTO = initRateResponseDTO(provider, currencyCode, rate);
      rateResponseDTO.success();
      return rateResponseDTO;
   }

   private RateResponseDTO initRateResponseDTO(String provider, String currencyCode, String rate) {
      RateResponseDTO rateResponseDTO = new RateResponseDTO();
      rateResponseDTO.setProvider( provider );
      rateResponseDTO.setCurrencyCode( currencyCode );
      rateResponseDTO.setDate( LocalDateTime.now() );
      rateResponseDTO.setRate( rate );
      return rateResponseDTO;
   }
}
