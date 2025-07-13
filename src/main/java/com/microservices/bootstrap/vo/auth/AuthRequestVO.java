package com.microservices.bootstrap.vo.auth;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class AuthRequestVO {

    @NotEmpty
    private String username;

    @NotEmpty
    private String password;

}
