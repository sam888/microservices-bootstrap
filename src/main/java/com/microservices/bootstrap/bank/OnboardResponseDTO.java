package com.microservices.bootstrap.bank;

/** Response body of POST /demo/bank/members. */
public record OnboardResponseDTO(long memberId, long accountId, String accountNumber) {
}
