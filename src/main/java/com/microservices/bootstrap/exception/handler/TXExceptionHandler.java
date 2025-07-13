package com.microservices.bootstrap.exception.handler;

import com.microservices.bootstrap.exception.InternalException;
import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class TXExceptionHandler {

    
    @ExceptionHandler( {InternalException.class } )
    protected ResponseEntity<BaseResponseVO> handleOutcomeCode(InternalException internalException) {
        log.error("Internal Exception: ", internalException);

        BaseResponseVO errorResponse = new BaseResponseVO( internalException );
        return new ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public final ResponseEntity<Object> runtimeExceptions(RuntimeException ex) {
        
        String errorMessage = "Unexpected runtime error detected....";
        log.error("RuntimeException: {}", errorMessage, ex);
        BaseResponseVO errorResponse = new BaseResponseVO();
        errorResponse.setOutcomeCode( "500" );
        errorResponse.setOutcomeMessage( errorMessage );
        
        return new ResponseEntity(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler( {WebExchangeBindException.class} )
    public ResponseEntity<BaseResponseVO> handleWebExchangeBindException(WebExchangeBindException ex) {

        Map<String, String> errorMessageMap = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errorMessageMap.put(fieldName, errorMessage);
        });

        BaseResponseVO errorResponse = new BaseResponseVO();
        errorResponse.setOutcomeCode( "-1" );
        errorResponse.setOutcomeMessage( "Invalid input data" );
        errorResponse.setErrorMessages( errorMessageMap );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}
