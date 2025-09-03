package com.citacita.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

@Service
public class AzureStreamService {

    private final WebClient webClient;

    public AzureStreamService(@Value("${azure.openai.endpoint}") String endpoint,
                              @Value("${azure.openai.apiKey}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public Flux<String> streamChat(Map<String, Object> body) {
        return webClient.post()
                .uri("/models/chat/completions?api-version=2024-05-01-preview")
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class);
    }
}
