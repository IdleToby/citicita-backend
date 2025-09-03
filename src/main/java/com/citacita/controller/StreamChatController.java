package com.citacita.controller;

import com.citacita.service.AzureStreamService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/stream-chat")
public class StreamChatController {

    private final AzureStreamService azureStreamService;

    public StreamChatController(AzureStreamService azureStreamService) {
        this.azureStreamService = azureStreamService;
    }

    @PostMapping(produces = "text/event-stream")
    public Flux<String> streamChat(@RequestBody Map<String, Object> body) {
        return azureStreamService.streamChat(body);
    }
}

