package com.microservices.bootstrap.bank.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry transient failures 3 times (1s apart), then publish the record to "<topic>.DLT".
 * Malformed payloads skip the retries. Spring Boot wires this bean into the listener container factory, so it applies
 * to every listener, each in its own consumer group: a failure in one group (say ledger-posting) is retried and
 * dead-lettered by that group alone, while the other groups carry on.
 *
 * All groups that read a topic share its "<topic>.DLT". The header kafka_dlt-original-consumer-group says which
 * consumer gave up. Production setups often give each consumer its own DLT instead (a custom destination resolver).
 */
@Configuration
public class BankKafkaErrorConfig {

    @Bean
    DefaultErrorHandler bankKafkaErrorHandler(KafkaOperations<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
