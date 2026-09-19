package com.microservices.bootstrap.client;

import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.CardDetailsResponseVO;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


/**
 * @author samuel.huang
 * Created: 13-July-2025
 */
@Service
public class TransactionClient {

   private final WebClient transactionWebClient;

   public TransactionClient(WebClient transactionWebClient) {
      this.transactionWebClient = transactionWebClient;

   }

   public Mono<ApiResponseVO<CardDetailsResponseVO>> getCardDetails(String moduleCode, String cardNumber){
      return transactionWebClient.get().uri( "/demo-cards/" + cardNumber ) // DemoCardController will serve this request
              .header("moduleCode", moduleCode)
              .retrieve().bodyToMono( new ParameterizedTypeReference<>(){} );
   }

}
