package com.microservices.bootstrap.bank.producer;

import com.microservices.bootstrap.bank.producer.BankExceptions.InsufficientFundsException;
import com.microservices.bootstrap.bank.producer.BankExceptions.NotFoundException;
import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain failures of the bank controllers (BankDemoController and MemberController) to the
 * project's standard error body (BaseResponseVO). Scoped to the bank packages and ordered first, so it wins over the
 * catch-all RuntimeException handler in TXExceptionHandler (which would turn every one of these into a 500).
 * Controllers outside com.microservices.bootstrap.bank are not affected. Bean-validation errors are not handled
 * here and still reach TXExceptionHandler.
 */
@RestControllerAdvice(basePackages = "com.microservices.bootstrap.bank")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BankExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<BaseResponseVO> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<BaseResponseVO> insufficient(InsufficientFundsException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<BaseResponseVO> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Includes DuplicateKeyException (e.g. a member with this email already exists). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<BaseResponseVO> conflict(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "Request violates a data constraint (e.g. this email is already registered)");
    }

    private static ResponseEntity<BaseResponseVO> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new BaseResponseVO("-1", message, null));
    }
}
