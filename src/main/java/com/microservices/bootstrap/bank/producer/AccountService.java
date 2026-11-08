package com.microservices.bootstrap.bank.producer;

import com.microservices.bootstrap.bank.producer.BankExceptions.NotFoundException;
import com.microservices.bootstrap.bank.AccountViewDTO;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/** Read side for accounts. Controllers call this instead of touching the database themselves. */
@Service
public class AccountService {

    private final DatabaseClient db;

    public AccountService(DatabaseClient db) {
        this.db = db;
    }

    public Mono<AccountViewDTO> getAccount(long accountId) {
        return db.sql("SELECT * FROM get_account(:accountId)")
                .bind("accountId", accountId)
                .map((row, meta) -> new AccountViewDTO(
                        row.get("id", Long.class),
                        row.get("member_id", Long.class),
                        row.get("account_number", String.class),
                        row.get("balance", BigDecimal.class),
                        row.get("currency", String.class).trim()))
                .one()
                .switchIfEmpty(Mono.error(new NotFoundException("Account " + accountId + " not found")));
    }
}
