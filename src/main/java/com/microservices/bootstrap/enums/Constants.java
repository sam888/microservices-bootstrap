package com.microservices.bootstrap.enums;

/**
 * @author samuel.huang
 * @Date 07-July-2025
 */
public enum Constants {

    MDC_KEY( "MDC_KEY" ),
    X_REQUEST_ID( "X-Request-ID" );

    private final String value;

    Constants(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
