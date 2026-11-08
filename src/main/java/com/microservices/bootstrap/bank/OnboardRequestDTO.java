package com.microservices.bootstrap.bank;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Request body of POST /demo/bank/members. */
public record OnboardRequestDTO(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 2) BigDecimal initialDeposit) {
}
