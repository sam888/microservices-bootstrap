package com.microservices.bootstrap.bank.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The part of Debezium's change-event envelope this consumer needs (JSON converter, schemas disabled).
 * op: c = create, u = update, d = delete, r = snapshot read, t = truncate.
 * Only the row id is read; everything else is ignored on purpose, so adding columns to the table never breaks us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemberChangeEvent(String op, Row before, Row after) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(Long id) {
    }
}
