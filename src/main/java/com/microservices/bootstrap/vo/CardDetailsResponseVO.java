package com.microservices.bootstrap.vo;

import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = false)
public class CardDetailsResponseVO extends BaseResponseVO {

   private String cardNumber;

   private LocalDate expiryDate;

   private Long traderId;

   private BigDecimal balance;
}
