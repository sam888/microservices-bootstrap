package com.microservices.bootstrap.client;

import com.microservices.bootstrap.dto.WestpacRateRequestDTO;
import com.microservices.bootstrap.dto.WestpacRateResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RegisterReflectionForBinding({ WestpacRateRequestDTO.class, WestpacRateResponseDTO.class })
public class WestpacClient {

   private final WebClient westpacWebClient;

   public WestpacClient(WebClient westpacWebClient) {
      this.westpacWebClient = westpacWebClient;
   }

   public Mono<WestpacRateResponseDTO> getUsdRateByWestpac(){
      WestpacRateRequestDTO westpacRateRequestDTO = getWestpacRateRequestDTO( "USD" );
      log.info("westpacRateRequestDTO: " + westpacRateRequestDTO);
      return westpacWebClient
              .post()
              .accept( MediaType.APPLICATION_JSON )
              .body( Mono.just( westpacRateRequestDTO ), WestpacRateRequestDTO.class )
              .retrieve()
              .bodyToMono( new ParameterizedTypeReference<>() {}  );
   }

   private WestpacRateRequestDTO getWestpacRateRequestDTO(String foreignCurrency) {
      WestpacRateRequestDTO westpacRateRequestDTO = new WestpacRateRequestDTO();
      westpacRateRequestDTO.setAction( "WESTPAC_BUYS_NZD" );
      westpacRateRequestDTO.setNZDAmount( "10" );
      westpacRateRequestDTO.setForeignCurrency( foreignCurrency );
      return westpacRateRequestDTO;
   }

}
