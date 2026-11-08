package com.microservices.bootstrap.bank.producer;

import com.microservices.bootstrap.bank.producer.BankExceptions.InsufficientFundsException;
import com.microservices.bootstrap.bank.producer.BankExceptions.NotFoundException;
import io.r2dbc.spi.R2dbcException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Translates the errors raised by the stored functions (see cdc-platform/db/09_functions_member_account.sql)
 * into the domain exceptions that BankExceptionHandler maps to HTTP statuses.
 *
 * The functions signal business failures with custom SQLSTATE codes:
 * <pre>
 *   UB001 not found           -> NotFoundException            (404)
 *   UB002 insufficient funds  -> InsufficientFundsException   (422)
 *   UB003 invalid request     -> IllegalArgumentException     (400)
 *   23xxx constraint violated -> DataIntegrityViolationException (409), e.g. duplicate email
 * </pre>
 * Anything else is passed through unchanged. Use as {@code .onErrorMap(ProcedureErrors::map)}.
 */
final class ProcedureErrors {

    private ProcedureErrors() {
    }

    static Throwable map(Throwable error) {
        R2dbcException db = findR2dbcException(error);
        if (db == null || db.getSqlState() == null) {
            return error;
        }
        String sqlState = db.getSqlState();
        return switch (sqlState) {
            case "UB001" -> new NotFoundException(db.getMessage());
            case "UB002" -> new InsufficientFundsException(db.getMessage());
            case "UB003" -> new IllegalArgumentException(db.getMessage());
            default -> sqlState.startsWith("23") ? new DataIntegrityViolationException(db.getMessage(), error) : error;
        };
    }

    /** Spring wraps the driver's exception, so look through the cause chain (bounded, in case of cycles). */
    private static R2dbcException findR2dbcException(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause()) {
            if (current instanceof R2dbcException r2dbc) {
                return r2dbc;
            }
        }
        return null;
    }
}
