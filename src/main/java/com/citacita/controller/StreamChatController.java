package com.citacita.controller;

import com.citacita.service.AzureStreamService;
// import com.citacita.service.FAQBasedRAGService; 
import com.citacita.service.EnhancedFAQRAGService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StreamChatController {

    private final AzureStreamService azureStreamService;
    private final EnhancedFAQRAGService ragService;

    public StreamChatController(AzureStreamService azureStreamService, 
                                EnhancedFAQRAGService ragService) { 
    this.azureStreamService = azureStreamService;
    this.ragService = ragService;
}


    /**
     * Chat Completions SSE - 带 RAG 增强
     */
    @PostMapping(value = "/stream-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody Map<String, Object> body) {
        // 1. 提取用户最新消息
        String userQuery = extractLatestUserMessage(body);
        
        // 2. 如果没有用户消息，直接调用原始服务
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return azureStreamService.streamChat(body);
        }
        
        // 3. 进行 RAG 检索，然后注入内容
        return ragService.retrieveRelevantContent(userQuery)
                .map(ragContent -> injectRAGContent(body, ragContent))
                .flatMapMany(enhancedBody -> azureStreamService.streamChat(enhancedBody))
                .onErrorResume(error -> {
                    // RAG 失败时，降级到原始聊天
                    System.err.println("RAG failed, fallback to normal chat: " + error.getMessage());
                    return azureStreamService.streamChat(body);
                });
        
    }
      

    /**
     * 提取用户最新消息
     */
    private String extractLatestUserMessage(Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
            
            if (messages == null || messages.isEmpty()) {
                return null;
            }
            
            // 从后往前找最新的用户消息
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, String> message = messages.get(i);
                if ("user".equals(message.get("role"))) {
                    return message.get("content");
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting user message: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * 将 RAG 内容注入到消息体中
     */
    private Map<String, Object> injectRAGContent(Map<String, Object> originalBody, String ragContent) {
        // 深拷贝原始 body
        Map<String, Object> enhancedBody = new HashMap<>(originalBody);
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> originalMessages = (List<Map<String, String>>) originalBody.get("messages");
        List<Map<String, String>> enhancedMessages = new ArrayList<>();
        
        // 复制所有消息
        for (Map<String, String> msg : originalMessages) {
            enhancedMessages.add(new HashMap<>(msg));
        }
        
        // 在系统消息中注入 RAG 内容
        injectAsSystemMessage(enhancedMessages, ragContent);        
        
        enhancedBody.put("messages", enhancedMessages);
        
        // 调试：打印增强后的消息
        System.out.println("=== RAG Enhanced Messages ===");
        enhancedMessages.forEach(msg -> 
            System.out.println(msg.get("role") + ": " + 
                (msg.get("content").length() > 300 ? 
                    msg.get("content").substring(0, 300) + "..." : 
                    msg.get("content"))
            )
        );
        
        return enhancedBody;
    }

    private void injectAsSystemMessage(List<Map<String, String>> messages, String ragContent) {
        boolean hasSystemMessage = messages.stream()
                .anyMatch(msg -> "system".equals(msg.get("role")));
        
        String systemContent;
        if (hasSystemMessage) {
            for (Map<String, String> msg : messages) {
                if ("system".equals(msg.get("role"))) {
                    systemContent = msg.get("content") + "\n\n[相关资料]\n" + ragContent;
                    msg.put("content", systemContent);
                    break;
                }
            }
        } else {
            // 🔥 根据RAG内容检测语言
            boolean isEnglish = ragContent.contains("Sorry, your question") || 
                               ragContent.contains("I'm specifically designed");
            
            if (isEnglish) {
                systemContent = "You are CitaCita's intelligent assistant. Please answer user questions based on the following information:\n\n[Reference Information]\n" + ragContent;
            } else {
                systemContent = "你是 CitaCita 的智能助手。请基于以下相关资料回答用户问题：\n\n[相关资料]\n" + ragContent;
            }
            
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemContent);
            
            messages.add(0, systemMessage);
        }
    }


    /**
     * Speech-to-Text
     */
    @PostMapping(value = "/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> transcribeBatch(@RequestPart("audio") Mono<FilePart> filePartMono) {
        return azureStreamService.transcribeBatch(filePartMono);
    }
}
