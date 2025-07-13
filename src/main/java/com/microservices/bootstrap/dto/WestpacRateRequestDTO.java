package com.microservices.bootstrap.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


/**
 * @author samuel.huang
 * @Date 17-Feb-2025
 */
@Data
public class WestpacRateRequestDTO {

    private String action;

    @JsonProperty("NZDAmount")
    private String NZDAmount;
    
    private String foreignCurrency;
}
