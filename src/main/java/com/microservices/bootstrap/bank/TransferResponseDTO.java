package com.microservices.bootstrap.bank;

import java.math.BigDecimal;
import java.util.UUID;

/** Response body of POST /demo/bank/transfers: the transfer id and both balances after the transfer. */
public record TransferResponseDTO(UUID transferId, BigDecimal fromBalance, BigDecimal toBalance) {
}
