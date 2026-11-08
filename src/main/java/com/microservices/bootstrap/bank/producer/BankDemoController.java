package com.microservices.bootstrap.bank.producer;

import com.microservices.bootstrap.bank.AccountViewDTO;
import com.microservices.bootstrap.bank.TransferRequestDTO;
import com.microservices.bootstrap.bank.TransferResponseDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Producer side of the demo, driven from Postman. HTTP only: every database access goes through a service class.
 * Transfers and account reads. The member endpoints (POST /demo/bank/members to onboard, and the Redis-cached
 * GET /demo/bank/members/{id}/profile) are in consumer.MemberController.
 * Mounted under /demo so JWTFilter lets it through without a token (same convention as the other demo controllers).
 * A real API would sit behind JWT; remove the /demo prefix to get that.
 */
@RestController
@RequestMapping("/demo/bank")
public class BankDemoController {

    private final TransferService transfers;
    private final AccountService accounts;

    public BankDemoController(TransferService transfers, AccountService accounts) {
        this.transfers = transfers;
        this.accounts = accounts;
    }

    @PostMapping("/transfers")
    public Mono<TransferResponseDTO> transfer(@Valid @RequestBody TransferRequestDTO request) {
        return transfers.transfer(request);
    }

    @GetMapping("/accounts/{id}")
    public Mono<AccountViewDTO> account(@PathVariable long id) {
        return accounts.getAccount(id);
    }
}
