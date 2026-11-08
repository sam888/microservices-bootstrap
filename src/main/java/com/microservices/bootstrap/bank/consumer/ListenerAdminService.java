package com.microservices.bootstrap.bank.consumer;

import com.microservices.bootstrap.bank.producer.BankExceptions.NotFoundException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

/**
 * Stops and starts ONE consumer group's listener while the rest of the app keeps running.
 *
 * Why: Kafka resets a group's offsets only while the group has no active members. Stopping just that listener's
 * container makes this instance leave the group, so the group can be reset (CLI or Conduktor) without stopping the
 * other consumers; starting it again rejoins at the new position. With several app instances, stop the listener on
 * every instance (or scale down) before resetting.
 *
 * Only the bank consumers are exposed. Their listener id equals their groupId, so the id in the URL is the group.
 */
@Service
public class ListenerAdminService {

    /** The listeners this service may control, in display order. */
    static final List<String> LISTENER_IDS =
            List.of(MemberChangeListener.CONSUMER, DomainEventHandler.CONSUMER, LedgerProcessor.CONSUMER);

    /** Upper bound for a clean stop: the record in flight finishes and its offset is committed first. */
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);

    private final KafkaListenerEndpointRegistry registry;

    public ListenerAdminService(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    public Flux<ListenerStatus> all() {
        return Flux.fromIterable(LISTENER_IDS).map(id -> toStatus(container(id)));
    }

    public Mono<ListenerStatus> status(String id) {
        return Mono.fromCallable(() -> toStatus(container(id)));
    }

    /**
     * Stops the listener and completes once its consumer has left the group. Non-blocking: the asynchronous
     * stop(callback) is used, so the WebFlux thread never waits for the consumer thread.
     */
    public Mono<ListenerStatus> stop(String id) {
        return Mono.defer(() -> {
            MessageListenerContainer c = container(id);
            if (!c.isRunning()) {
                return Mono.just(toStatus(c));                        // already stopped: nothing to do
            }
            return Mono.<Void>create(done -> c.stop(() -> done.success()))
                    .timeout(STOP_TIMEOUT)
                    .then(Mono.fromCallable(() -> toStatus(c)));
        });
    }

    /** Starts the listener; it rejoins the group and continues from the group's committed offsets. */
    public Mono<ListenerStatus> start(String id) {
        return Mono.fromCallable(() -> {
                    MessageListenerContainer c = container(id);
                    c.start();                                        // may wait briefly for the consumer thread
                    return toStatus(c);
                })
                .subscribeOn(Schedulers.boundedElastic());           // so that wait never lands on a Netty thread
    }

    private MessageListenerContainer container(String id) {
        MessageListenerContainer c = LISTENER_IDS.contains(id) ? registry.getListenerContainer(id) : null;
        if (c == null) {
            throw new NotFoundException("No Kafka listener '" + id + "'. Known listeners: " + LISTENER_IDS);
        }
        return c;
    }

    private static ListenerStatus toStatus(MessageListenerContainer c) {
        return new ListenerStatus(c.getListenerId(), c.getGroupId(), c.isRunning());
    }
}
