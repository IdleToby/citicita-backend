// package com.citacita.service;

// import com.citacita.config.AzureOpenAIConfig;
// import com.citacita.dto.ChatRequest;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.http.HttpStatusCode;
// import org.springframework.http.MediaType;
// import org.springframework.stereotype.Service;
// import org.springframework.web.reactive.function.client.WebClient;
// import reactor.core.publisher.Flux;
// import reactor.core.publisher.FluxSink;
// import reactor.core.scheduler.Schedulers;

// import java.time.Duration;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;

// @Service
// @Slf4j
// public class ResumeChatService {

//     private final WebClient openAiClient;
//     private final AzureOpenAIConfig azureConfig;
//     private final ObjectMapper objectMapper;

//     public ResumeChatService(AzureOpenAIConfig azureConfig, ObjectMapper objectMapper) {
//         this.azureConfig = azureConfig;
//         this.objectMapper = objectMapper;
//         this.openAiClient = WebClient.builder()
//                 .baseUrl(azureConfig.getEndpoint())
//                 .defaultHeader("Authorization", "Bearer " + azureConfig.getApiKey())
//                 .defaultHeader("Content-Type", "application/json")
//                 .build();
//     }

//     public Flux<String> streamChat(ChatRequest request) {
//         log.info("=== ResumeChatService.streamChat 开始 ===");
//         log.info("请求消息数量: {}", request.getMessages().size());
//         log.info("Azure端点: {}", azureConfig.getEndpoint());

//         return Flux.<String>create(sink -> {
//             try {
//                 // 1. 构建请求体
//                 Map<String, Object> body = buildRequestBody(request);

//                 // 2. 调用 Azure OpenAI
//                 streamAzureOpenAI(body, sink);

//             } catch (Exception e) {
//                 log.error("简历聊天流式处理失败", e);
//                 handleError(sink, e);
//             }
//         })
//         .subscribeOn(Schedulers.boundedElastic()) // 异步调度
//         .timeout(Duration.ofMinutes(2)); // 超时保护
//     }

//     private void streamAzureOpenAI(Map<String, Object> body, FluxSink<String> sink) {
//         openAiClient.post()
//                 .uri("/models/chat/completions?api-version=2024-05-01-preview")
//                 .bodyValue(body)
//                 .accept(MediaType.TEXT_EVENT_STREAM)
//                 .retrieve()
//                 .onStatus(
//                         HttpStatusCode::isError,
//                         clientResponse -> clientResponse.bodyToMono(String.class)
//                                 .flatMap(errorBody -> {
//                                     log.error("OpenAI API Error: {}", errorBody);
//                                     return reactor.core.publisher.Mono.error(
//                                             new RuntimeException("OpenAI API failed: " + errorBody)
//                                     );
//                                 })
//                 )
//                 .bodyToFlux(String.class)
//                 .map(this::cleanAzureResponse)
//                 .doOnNext(sink::next)
//                 .doOnError(e -> handleError(sink, e))
//                 .doOnComplete(sink::complete)
//                 .subscribe();
//     }

//     // 构建请求体
//     private Map<String, Object> buildRequestBody(ChatRequest request) {
//         Map<String, Object> body = new HashMap<>();
//         body.put("messages", request.getMessages());
//         body.put("temperature", azureConfig.getTemperature());
//         body.put("max_tokens", azureConfig.getMaxTokens());

//         body.put("model", request.getModel() != null ? request.getModel() : "gpt-oss-120b");

//         return body;
//     }

//     // 清理 Azure 响应（避免奇怪符号）
//     private String cleanAzureResponse(String response) {
//         return response
//                 .replace("’ ", "'")
//                 .replace("’", "'");
//     }

//     // 错误处理
//     private void handleError(FluxSink<String> sink, Throwable e) {
//         try {
//             String errorJson = objectMapper.writeValueAsString(Map.of("error", e.getMessage()));
//             sink.next("data: " + errorJson + "\n\n");
//         } catch (Exception ex) {
//             sink.next("data: {\"error\":\"" + e.getMessage() + "\"}\n\n");
//         }
//         sink.complete();
//     }
// }
package com.citacita.service;

import com.citacita.dto.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeChatService {
    
    // 注入你现有的 AzureStreamService
    private final AzureStreamService azureStreamService;
    
    public Flux<String> streamChat(ChatRequest request) {
        log.info("=== ResumeChatService.streamChat 开始 ===");
        log.info("请求消息数量: {}", request.getMessages().size());
        
        // 1. 增强消息内容 - 添加简历分析上下文
        List<ChatRequest.ChatMessage> enhancedMessages = enhanceMessagesForResume(request);
        
        // 2. 构建请求体
        Map<String, Object> body = buildRequestBody(enhancedMessages, request);
        
        log.info("发送到Azure的请求: {}", body);
        
        // 3. 调用AzureStreamService并打印原始输出
        return azureStreamService.streamChat(body)
                .doOnNext(chunk -> {
                    // 🔥 关键调试：打印AzureStreamService的原始输出
                    log.info("🔍 AzureStreamService原始输出: [{}]", chunk);
                    log.info("🔍 是否包含data前缀: {}", chunk.startsWith("data:"));
                })
                .doOnComplete(() -> log.info("✅ ResumeChatService stream completed"));
    }
    
    private List<ChatRequest.ChatMessage> enhanceMessagesForResume(ChatRequest request) {
        List<ChatRequest.ChatMessage> enhancedMessages = new ArrayList<>();
        
        // 添加简历专用的系统提示
        String resumeSystemPrompt = buildResumeSystemPrompt(request.getLanguage());
        enhancedMessages.add(new ChatRequest.ChatMessage("system", resumeSystemPrompt));
        
        // 添加原始消息，跳过原来的system消息（如果有）
        for (ChatRequest.ChatMessage msg : request.getMessages()) {
            if (!"system".equals(msg.getRole())) {
                // 增强用户消息，添加简历相关上下文
                if ("user".equals(msg.getRole())) {
                    String enhancedContent = enhanceUserMessageForResume(msg.getContent());
                    enhancedMessages.add(new ChatRequest.ChatMessage("user", enhancedContent));
                } else {
                    enhancedMessages.add(msg);
                }
            }
        }
        
        return enhancedMessages;
    }
    
    private Map<String, Object> buildRequestBody(List<ChatRequest.ChatMessage> messages, ChatRequest request) {
        Map<String, Object> body = new HashMap<>();
        
        // 转换消息格式为Azure需要的格式
        List<Map<String, Object>> azureMessages = messages.stream()
                .map(msg -> {
                    Map<String, Object> msgMap = new HashMap<>();
                    msgMap.put("role", msg.getRole());
                    msgMap.put("content", msg.getContent());
                    return msgMap;
                })
                .toList();
        
        body.put("messages", azureMessages);
        body.put("stream", true);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2000);
        
        // 如果请求中有model，使用它，否则使用默认的
        if (request.getModel() != null) {
            body.put("model", request.getModel());
        }
        
        return body;
    }
    
    private String buildResumeSystemPrompt(String language) {
        switch (language != null ? language : "en") {
            case "zh-CN":
                return """
                    你是CitaCita的专业简历分析师和职业顾问。请严格按照以下要求回答：
                    
                    【专业领域】
                    - 简历结构优化和内容改进
                    - 工作经历描述和量化成就
                    - 技能展示和关键词优化
                    - ATS系统适配建议
                    - 职业发展规划建议
                    
                    【回答风格】
                    - 提供具体、可操作的建议
                    - 使用专业但易懂的语言
                    - 给出实际的例子和模板
                    - 关注求职成功率提升
                    
                    现在请基于用户的简历相关问题，提供专业建议。
                    """;
                    
            case "en":
                return """
                    You are a professional resume analyst and career counselor for CitaCita. 
                    
                    【PROFESSIONAL AREAS】
                    - Resume structure optimization and content improvement
                    - Work experience description and quantified achievements
                    - Skills presentation and keyword optimization
                    - ATS system compatibility recommendations
                    - Career development planning advice
                    
                    【RESPONSE STYLE】
                    - Provide specific, actionable recommendations
                    - Use professional but understandable language
                    - Give practical examples and templates
                    - Focus on improving job search success rates
                    
                    Please provide professional advice based on the user's resume-related questions.
                    """;
                    
            case "ms":
                return """
                    Anda adalah penganalisis resume profesional dan kaunselor kerjaya untuk CitaCita.
                    
                    【BIDANG PROFESIONAL】
                    - Pengoptimuman struktur resume dan penambahbaikan kandungan
                    - Penerangan pengalaman kerja dan pencapaian terkuantiti
                    - Persembahan kemahiran dan pengoptimuman kata kunci
                    - Cadangan keserasian sistem ATS
                    - Nasihat perancangan pembangunan kerjaya
                    
                    【GAYA RESPONS】
                    - Berikan cadangan khusus dan boleh dilaksanakan
                    - Gunakan bahasa profesional tetapi mudah difahami
                    - Berikan contoh praktikal dan templat
                    - Fokus pada meningkatkan kadar kejayaan pencarian kerja
                    
                    Sila berikan nasihat profesional berdasarkan soalan berkaitan resume pengguna.
                    """;
                    
            default:
                return buildResumeSystemPrompt("en");
        }
    }
    
    private String enhanceUserMessageForResume(String originalMessage) {
        // 添加简历相关的上下文标签，帮助AI理解这是简历相关的问题
        return "[简历优化咨询 Resume Consultation] " + originalMessage;
    }
}