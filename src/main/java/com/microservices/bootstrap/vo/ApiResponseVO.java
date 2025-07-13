package com.microservices.bootstrap.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author samuel.huang
 * Createdd: 17-Oct-2024
 */
@Data 
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
public class ApiResponseVO<T extends BaseResponseVO> extends BaseResponseVO {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"outcomeCode", "outcomeMessage", "outcomeUserMessage"})
    private T data;

    public ApiResponseVO(T data) {
        this( data, true );
    }

    public ApiResponseVO(T data, boolean showData) {
        super( data );
        if ( showData ) {
            this.data = data;
        }
    }

}
