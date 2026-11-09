package com.mesac.controller;

import com.mesac.service.LiveHub;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** The browser opens this once and receives live updates for as long as the page is open. */
@RestController
public class StreamController {

    private final LiveHub live;

    public StreamController(LiveHub live) {
        this.live = live;
    }

    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return live.subscribe();
    }
}
