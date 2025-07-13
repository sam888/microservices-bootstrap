package com.microservices.bootstrap.vo.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.microservices.bootstrap.exception.InternalException;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @author samuel.huang 
 * Created: 15-Feb-2025
 * 
 */
@Data
@NoArgsConstructor
public class BaseResponseVO {

    private String outcomeCode;
    private String outcomeMessage;
    private String outcomeUserMessage;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> errorMessages;

    public BaseResponseVO(String outcomeCode, String outcomeMessage, String outcomeUserMessage) {
        this.outcomeCode = outcomeCode;
        this.outcomeMessage = outcomeMessage;
        this.outcomeUserMessage = outcomeUserMessage;
    }
    
    public BaseResponseVO(InternalException internalException) {
        this.outcomeCode = internalException.getOutcomeCode();
        this.outcomeMessage = internalException.getOutcomeMessage();
        this.outcomeUserMessage = internalException.getOutcomeUserMessage();
    }

    public BaseResponseVO(BaseResponseVO baseResponseVO) {
        this.outcomeCode = baseResponseVO.getOutcomeCode();
        this.outcomeMessage = baseResponseVO.getOutcomeMessage();
        this.outcomeUserMessage = baseResponseVO.getOutcomeUserMessage();
    }
    
    public void success() {
        setOutcomeCode( "0" );
        setOutcomeMessage( "Success" );
    }

    public void error(String errorMessage){
        this.setOutcomeCode( "-1" );
        this.setOutcomeMessage( errorMessage );
    }

    public static BaseResponseVO success(String message) {
        return new BaseResponseVO("0", "Success", message);
    }
}
