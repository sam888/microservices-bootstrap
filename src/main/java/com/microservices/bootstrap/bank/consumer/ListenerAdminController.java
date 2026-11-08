package com.microservices.bootstrap.bank.consumer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Operations endpoint for the bank's Kafka consumers: see their state, and stop or start ONE consumer group's
 * listener without stopping the app (for example to reset that group's offsets). HTTP only: the work is in
 * {@link ListenerAdminService}.
 *
 *   GET  /demo/admin/listeners              state of every bank listener
 *   GET  /demo/admin/listeners/{id}         state of one
 *   POST /demo/admin/listeners/{id}/stop    leave the group (returns once stopped)
 *   POST /demo/admin/listeners/{id}/start   rejoin the group
 *
 * The id is the consumer group: bootstrap-member-cache, bootstrap-domain-events or ledger-posting. Unknown id: 404.
 *
 * Mounted under /demo so JWTFilter lets it through without a token, like the other demo endpoints. A real service
 * must put an operations endpoint like this behind authentication and an admin role (or on a management port).
 */
@RestController
@RequestMapping("/demo/admin/listeners")
public class ListenerAdminController {

    private final ListenerAdminService listeners;

    public ListenerAdminController(ListenerAdminService listeners) {
        this.listeners = listeners;
    }

    @GetMapping
    public Flux<ListenerStatus> all() {
        return listeners.all();
    }

    @GetMapping("/{id}")
    public Mono<ListenerStatus> status(@PathVariable String id) {
        return listeners.status(id);
    }

    @PostMapping("/{id}/stop")
    public Mono<ListenerStatus> stop(@PathVariable String id) {
        return listeners.stop(id);
    }

    @PostMapping("/{id}/start")
    public Mono<ListenerStatus> start(@PathVariable String id) {
        return listeners.start(id);
    }
}
