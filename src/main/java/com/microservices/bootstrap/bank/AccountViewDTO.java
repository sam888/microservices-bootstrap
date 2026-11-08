package com.microservices.bootstrap.bank;

import java.math.BigDecimal;

/** Response body of GET /demo/bank/accounts/{id}. */
public record AccountViewDTO(long id, long memberId, String accountNumber, BigDecimal balance, String currency) {
}
