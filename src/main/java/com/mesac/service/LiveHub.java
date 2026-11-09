package com.mesac.service;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes live updates to every open admin page / big screen using Server-Sent Events.
 * (This replaces Socket.IO from the old Node prototype. No extra library needed.)
 *
 * Event names sent: "match", "match-deleted", "event", "poll", "teams", "sports", "records", "gate".
 */
@Component
public class LiveHub {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // 0 = never time out
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("hello").data(Map.of("clients", emitters.size()), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                // Browser tab was closed - forget it.
                emitters.remove(emitter);
            }
        }
    }

    public int clientCount() {
        return emitters.size();
    }

    /** Small ping every 20s so proxies and browsers keep the connection open. */
    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        publish("ping", Map.of("clients", emitters.size()));
    }
}
