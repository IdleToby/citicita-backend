package com.citacita.controller;

import com.citacita.service.AzureStreamService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class StreamChatController {

    private final AzureStreamService azureStreamService;

    public StreamChatController(AzureStreamService azureStreamService) {
        this.azureStreamService = azureStreamService;
    }

    /**
     * Chat Completions SSE
     */
    @PostMapping(value = "/stream-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody Map<String, Object> body) {
        return azureStreamService.streamChat(body);
    }

    /**
     * Speech-to-Text
     */
    @PostMapping(value = "/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> transcribeBatch(@RequestPart("audio") Mono<FilePart> filePartMono) {
        return azureStreamService.transcribeBatch(filePartMono);
    }
}
