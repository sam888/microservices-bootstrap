package com.microservices.bootstrap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ApiListResponseDTO<T extends List> extends BaseResponseDTO {

    @JsonIgnoreProperties(value = {"outcomeCode", "outcomeMessage", "outcomeUserMessage"})
    private T data;

    public ApiListResponseDTO(T data) {
        this.data = data;
        success();
    }
}
