package com.microservices.bootstrap.client;

import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.CardDetailsResponseVO;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


/**
 * @author samuel.huang
 * Created: 13-July-2025
 */
@Service
public class TransactionClient extends BaseClient {

   private String transactionUrl;

   @Getter
   private WebClient transactionWebClient;

   public TransactionClient(@Value("${app.transaction-url}") String transactionUrl) {
      this.transactionUrl = transactionUrl;
      this.transactionWebClient = getWebClient( transactionUrl );

   }

   public Mono<ApiResponseVO<CardDetailsResponseVO>> getCardDetails(String moduleCode, String cardNumber){
      return getTransactionWebClient().get().uri( "/demo-cards/" + cardNumber ) // DemoCardController will serve this request
              .header("moduleCode", moduleCode)
              .retrieve().bodyToMono( new ParameterizedTypeReference<>(){} );
   }

}
