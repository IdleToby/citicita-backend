package com.citacita.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class AzureStreamService {

    private final WebClient openAiClient;
    private final WebClient speechClient;

    public AzureStreamService(
            @Value("${azure.openai.endpoint}") String openAiEndpoint,
            @Value("${azure.openai.apiKey}") String openAiKey,
            @Value("${azure.speech.endpoint}") String speechEndpoint,
            @Value("${azure.speech.apiKey}") String speechKey
    ) {
        // OpenAI Chat Completions
        this.openAiClient = WebClient.builder()
                .baseUrl(openAiEndpoint)
                .defaultHeader("Authorization", "Bearer " + openAiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        // Azure Speech-to-Text
        this.speechClient = WebClient.builder()
                .baseUrl(speechEndpoint)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Ocp-Apim-Subscription-Key", speechKey)
                .build();
    }

    /**
     * 调用 Azure OpenAI Chat
     */
        public Flux<String> streamChat(Map<String, Object> body) {
                return openAiClient.post()
                        .uri("/models/chat/completions?api-version=2024-05-01-preview")
                        .bodyValue(body)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .retrieve()
                        .onStatus(
                        HttpStatusCode::isError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                        System.err.println("OpenAI API Error: " + errorBody);
                                        return Mono.error(new RuntimeException("OpenAI API failed: " + errorBody));
                                })
                        )
                        .bodyToFlux(String.class)
                        .map(this::cleanAzureResponse); 
        }
        // 添加清理Azure响应的方法
        private String cleanAzureResponse(String response) {
                return response
                        .replace("’ ", "'")        // 替换特殊单引号
                        .replace("’", "'");        // 替换另一种单引号                        
        }

    public Mono<Map<String, Object>> transcribeBatch(Mono<FilePart> filePartMono) {
        return filePartMono.flatMap(filePart -> {
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("audio", filePart);
            bodyBuilder.part(
                    "definition",
                    "{\"locales\":[\"en-US\",\"zh-CN\"]}",
                    MediaType.APPLICATION_JSON
            );

            return speechClient.post()
                    .uri("/speechtotext/transcriptions:transcribe?api-version=2024-11-15")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    // Enhanced error handling to log response body
                    .onStatus(
                            HttpStatusCode::isError,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        System.err.println("Error Body: " + errorBody);
                                        return Mono.error(new RuntimeException("Request Failed with status: " + clientResponse.statusCode() + " and body: " + errorBody));
                                    })
                    )
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
        });
    }
}
