package com.microservices.bootstrap.bank.producer;

import com.microservices.bootstrap.bank.OnboardRequestDTO;
import com.microservices.bootstrap.bank.OnboardResponseDTO;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Onboards a member. The work happens in the Postgres function onboard_member(...): it inserts the member, the
 * account AND the outbox event in one transaction, so there is nothing to coordinate here and no @Transactional.
 * Called by MemberController (POST /demo/bank/members).
 */
@Service
public class OnboardingService {

    private final DatabaseClient db;

    public OnboardingService(DatabaseClient db) {
        this.db = db;
    }

    public Mono<OnboardResponseDTO> onboard(OnboardRequestDTO req) {
        return db.sql("SELECT * FROM onboard_member(:firstName, :lastName, :email, :initialDeposit)")
                .bind("firstName", req.firstName())
                .bind("lastName", req.lastName())
                .bind("email", req.email())
                .bind("initialDeposit", req.initialDeposit())
                .map((row, meta) -> new OnboardResponseDTO(
                        row.get("out_member_id", Long.class),
                        row.get("out_account_id", Long.class),
                        row.get("out_account_number", String.class)))
                .one()
                .onErrorMap(ProcedureErrors::map);
    }
}
