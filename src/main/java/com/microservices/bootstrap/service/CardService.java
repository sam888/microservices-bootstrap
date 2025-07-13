package com.microservices.bootstrap.service;

import com.microservices.bootstrap.client.TransactionClient;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.CardDetailsResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * @author: samuel.huang
 * Created: 13-July-2025
 *
 */
@Slf4j
@Service
public class CardService {

   private final TransactionClient transactionClient;

   public CardService(TransactionClient transactionClient) {
      this.transactionClient = transactionClient;
   }

   public Mono<ApiResponseVO<CardDetailsResponseVO>> getCardDetails(String moduleCode, String cardNumber) {
      return transactionClient.getCardDetails(moduleCode, cardNumber);
   }
}
