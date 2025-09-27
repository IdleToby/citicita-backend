package com.citacita.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class AzureStreamService {

    private final WebClient openAiClient;
    private final WebClient speechClient;
    private final WebClient ttsClient;
    private final WebClient sttClient;


    public AzureStreamService(
            @Value("${azure.openai.endpoint}") String openAiEndpoint,
            @Value("${azure.openai.apiKey}") String openAiKey,
            @Value("${azure.speech.endpoint}") String speechEndpoint,
            @Value("${azure.speech.apiKey}") String speechKey,
            @Value("${azure.tts.endpoint}") String ttsEndpoint,
            @Value("${azure.tts.apiKey}") String ttsKey,
            @Value("${azure.stt.endpoint}") String sttEndpoint,
            @Value("${azure.stt.apiKey}") String sttKey
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

        // Azure Text-to-Speech
        this.ttsClient = WebClient.builder()
                .baseUrl(ttsEndpoint)
                .defaultHeader("Ocp-Apim-Subscription-Key", ttsKey)
                .build();

        // Azure Speech-to-Text
        this.sttClient = WebClient.builder()
                .baseUrl(sttEndpoint)
                .defaultHeader("Ocp-Apim-Subscription-Key", sttKey)
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
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        });
    }

    public Mono<Map<String, Object>> resumePolish(Mono<FilePart> filePartMono) {
        // give a test return
        return filePartMono.map(filePart -> {
            // Once the FilePart is available, we create a map with mock data.
            Map<String, Object> responseMap = new HashMap<>();

            // Add some information to the map for testing purposes.
            responseMap.put("status", "POLISHED_SUCCESS");
            responseMap.put("originalFilename", filePart.filename()); // Acknowledge the received file.
            responseMap.put("timestamp", System.currentTimeMillis());
            responseMap.put("mockSuggestion", "Consider adding more quantifiable achievements.");

            // The map is returned, and the .map() operator wraps it in a Mono.
            return responseMap;
        });
    }

    public Flux<DataBuffer> tts(String text) {
        String ssmlBody = String.format(
                "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                        "<voice name='en-US-AvaMultilingualNeural'>%s</voice>" +
                        "</speak>",
                text
        );

        return ttsClient.post()
                .uri("/cognitiveservices/v1")
                .header("X-Microsoft-OutputFormat", "audio-16khz-32kbitrate-mono-mp3")
                .contentType(MediaType.valueOf("application/ssml+xml"))
                // accept 头不是必需的，但保留也可以
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(ssmlBody)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("[No response body]")
                                .flatMap(errorBody -> {
                                    System.err.println("Azure TTS API Error. Status: " + clientResponse.statusCode() + ", Body: " + errorBody);
                                    String errorMessage = String.format(
                                            "Azure TTS API failed with status: %s. Response: %s",
                                            clientResponse.statusCode(),
                                            errorBody
                                    );
                                    return Mono.error(new RuntimeException(errorMessage));
                                })
                )
                .bodyToFlux(DataBuffer.class);
    }

    public Mono<Map<String, Object>> pronunciationEvaluation(Mono<FilePart> filePartMono, String lang) {
        // 1. 定义发音评估的参数 (JSON 格式)
        // 关键点: "ReferenceText" 为空字符串，代表这是“无脚本”评估
        String pronAssessmentParamsJson = "{" +
                "\"ReferenceText\": \"\"," +
                "\"GradingSystem\": \"HundredMark\"," +
                "\"Granularity\": \"Phoneme\"," +
                "\"Dimension\": \"Comprehensive\"," +
                "\"EnableMiscue\": false" + // 无脚本模式下，杂项（漏读/增读）评估通常为 false
                "}";

        // 2. 将 JSON 参数进行 Base64 编码，以便放入请求头
        String pronAssessmentParamsBase64 = Base64.getEncoder()
                .encodeToString(pronAssessmentParamsJson.getBytes());

        // 3. 处理上传的音频文件并发送请求
        return filePartMono.flatMap(filePart -> {
            // 从 FilePart 获取音频内容
            Flux<DataBuffer> audioData = filePart.content();

            // 获取前端传递的 Content-Type，如果不存在则使用默认值
            // 注意：Azure 对 audio/webm 的支持有限，最可靠的格式是 audio/wav
            String contentType = filePart.headers().getContentType() != null ?
                    filePart.headers().getContentType().toString() :
                    "audio/wav"; // 推荐使用 WAV 格式

            return sttClient.post()
                    // 使用实时语音识别的端点，并指定语言和详细输出格式
                    .uri("/speech/recognition/conversation/cognitiveservices/v1?language=" + lang + "&format=detailed")
                    .header("Pronunciation-Assessment", pronAssessmentParamsBase64) // 添加发音评估的配置头
                    .header(HttpHeaders.CONTENT_TYPE, contentType) // 设置音频流的 Content-Type
                    .accept(MediaType.APPLICATION_JSON)
                    .body(audioData, DataBuffer.class)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        System.err.println("Pronunciation Evaluation Error. Status: " + clientResponse.statusCode() + ", Body: " + errorBody);
                                        return Mono.error(new RuntimeException("Azure API failed with status: " + clientResponse.statusCode() + " and body: " + errorBody));
                                    })
                    )
                    .bodyToMono(new ParameterizedTypeReference<>() {
                    });
        });
    }

    public Mono<String> generateQuestions(Map<String, Object> body) {
        return openAiClient.post()
                .uri("/models/chat/completions?api-version=2024-05-01-preview")
                .bodyValue(body)
                .accept(MediaType.APPLICATION_JSON) // 非流式返回 JSON
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    System.err.println("OpenAI API Error: " + errorBody);
                                    return Mono.error(new RuntimeException("OpenAI API failed: " + errorBody));
                                })
                )
                .bodyToMono(String.class) // 拿到完整响应
                .map(fullResponse -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(fullResponse);

                        // 提取出 choices[0].message.content
                        String content = root.path("choices").get(0).path("message").path("content").asText();

                        mapper.readTree(content); // 确认是合法 JSON
                        return content;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse response: " + fullResponse, e);
                    }
                });
    }

}
