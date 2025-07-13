package com.microservices.bootstrap.exception;

import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Samuel Huang
 * Created: 17-Feb-2025
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class InternalException extends Exception {

    private String outcomeCode;
    private String outcomeMessage;
    private String outcomeUserMessage;

    public InternalException(String outcomeCode, String outcomeMessage, String outcomeUserMessage) {
        this.outcomeCode = outcomeCode;
        this.outcomeMessage = outcomeMessage;
        this.outcomeUserMessage = outcomeUserMessage;
    }

    public InternalException(BaseResponseVO errorBody) {
        this.outcomeCode = errorBody.getOutcomeCode();
        this.outcomeMessage = errorBody.getOutcomeMessage();
        this.outcomeUserMessage = errorBody.getOutcomeUserMessage();
    }

}
