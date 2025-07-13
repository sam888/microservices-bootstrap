package com.microservices.bootstrap.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author samuel.huang
 * Created: 17-Feb-2025
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class RateResponseDTO extends BaseResponseDTO {
    
    private String provider;
    
    private String currencyCode;
    
    private String rate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime date;
}
