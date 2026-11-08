package com.microservices.bootstrap.bank.producer;

import com.microservices.bootstrap.bank.TransferRequestDTO;
import com.microservices.bootstrap.bank.TransferResponseDTO;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Transfers money. The work happens in the Postgres function transfer_funds(...): it locks both accounts (in id
 * order, so opposite transfers cannot deadlock), checks balance and currency, debits, credits and writes the
 * FundsTransferred outbox row, all in one transaction. A failure at any step undoes the others, so a rejected
 * transfer leaves no event behind.
 *
 * Failures come back as SQLSTATE codes and are translated by ProcedureErrors.
 */
@Service
public class TransferService {

    private final DatabaseClient db;

    public TransferService(DatabaseClient db) {
        this.db = db;
    }

    public Mono<TransferResponseDTO> transfer(TransferRequestDTO req) {
        return db.sql("SELECT * FROM transfer_funds(:fromAccountId, :toAccountId, :amount)")
                .bind("fromAccountId", req.fromAccountId())
                .bind("toAccountId", req.toAccountId())
                .bind("amount", req.amount())
                .map((row, meta) -> new TransferResponseDTO(
                        row.get("out_transfer_id", UUID.class),
                        row.get("out_from_balance", BigDecimal.class),
                        row.get("out_to_balance", BigDecimal.class)))
                .one()
                .onErrorMap(ProcedureErrors::map);
    }
}
