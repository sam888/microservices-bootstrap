package com.microservices.bootstrap.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ApiListResponseVO<T extends List> extends BaseResponseVO {

    @JsonIgnoreProperties(value = {"outcomeCode", "outcomeMessage", "outcomeUserMessage"})
    private T data;

    public ApiListResponseVO(T data) {
        this.data = data;
        success();
    }

}
