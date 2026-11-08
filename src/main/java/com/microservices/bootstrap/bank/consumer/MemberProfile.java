package com.microservices.bootstrap.bank.consumer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cached in Redis. A plain (non-final) class on purpose: RedisCacheConfig enables Jackson default typing for
 * NON_FINAL types only, so a Java record (which is final) would come back from Redis as a LinkedHashMap.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfile {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String address;
    private Short memberStatusId;
}
