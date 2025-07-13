package com.microservices.bootstrap.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;

/**
 * Courtesy of <a href="https://github.com/archie-swif/webflux-mdc/blob/master/src/main/java/com/example/webfluxmdc/MdcContextLifterConfiguration.java">..jc.</a>
 */
@Configuration
public class MdcContextLifterConfiguration {

    private final String MDC_CONTEXT_REACTOR_KEY = MdcContextLifterConfiguration.class.getName();
    
    @PostConstruct
    private void contextOperatorHook() {
        Hooks.onEachOperator(MDC_CONTEXT_REACTOR_KEY,
                Operators.lift((scannable, coreSubscriber) -> new MdcContextLifter<>(coreSubscriber))
        );
    }

    @PreDestroy
    private void cleanupHook() {
       Hooks.resetOnEachOperator(MDC_CONTEXT_REACTOR_KEY);
    }
} 
