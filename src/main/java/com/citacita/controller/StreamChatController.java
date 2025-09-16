package com.citacita.controller;

import com.citacita.service.AzureStreamService;
// import com.citacita.service.FAQBasedRAGService; 
import com.citacita.service.EnhancedFAQRAGService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class StreamChatController {

    private final AzureStreamService azureStreamService;
    private final EnhancedFAQRAGService ragService;

    // 语言检测的正则表达式模式
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern MALAY_PATTERN = Pattern.compile("\\b(?i)(saya|anda|adalah|dengan|untuk|ini|itu|yang|dan|atau|tidak|ya|bagaimana|kenapa|dimana|kapan|siapa|apa|boleh|tolong|terima kasih|maaf)\\b");

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
                .map(ragContent -> injectRAGContent(body, ragContent, userQuery))
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
     * 检测语言类型
     */
    private String detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "english"; // 默认英语
        }

        String lowerText = text.toLowerCase();

        // 检测中文
        if (CHINESE_PATTERN.matcher(text).find()) {
            return "chinese";
        }

        // 检测马来语关键词
        if (MALAY_PATTERN.matcher(lowerText).find()) {
            return "malay";
        }

        // 检测英语特定内容（如错误消息）
        if (lowerText.contains("sorry, your question") ||
                lowerText.contains("i'm specifically designed") ||
                lowerText.contains("english") ||
                lowerText.contains("please") ||
                lowerText.contains("thank you")) {
            return "english";
        }

        // 默认返回英语
        return "english";
    }

    /**
     * 将 RAG 内容注入到消息体中
     */
    private Map<String, Object> injectRAGContent(Map<String, Object> originalBody, String ragContent, String userQuery) {
        // 深拷贝原始 body
        Map<String, Object> enhancedBody = new HashMap<>(originalBody);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> originalMessages = (List<Map<String, String>>) originalBody.get("messages");
        List<Map<String, String>> enhancedMessages = new ArrayList<>();

        // 复制所有消息
        for (Map<String, String> msg : originalMessages) {
            enhancedMessages.add(new HashMap<>(msg));
        }

        // 检测语言并注入 RAG 内容
        String detectedLanguage = detectLanguage(ragContent + " " + userQuery);
        injectAsSystemMessage(enhancedMessages, ragContent, detectedLanguage);

        enhancedBody.put("messages", enhancedMessages);

        // 调试：打印检测到的语言和增强后的消息
        System.out.println("=== Detected Language: " + detectedLanguage + " ===");
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

    private void injectAsSystemMessage(List<Map<String, String>> messages, String ragContent, String language) {
        boolean hasSystemMessage = messages.stream()
                .anyMatch(msg -> "system".equals(msg.get("role")));

        String systemContent = generateSystemContent(ragContent, language);

        if (hasSystemMessage) {
            for (Map<String, String> msg : messages) {
                if ("system".equals(msg.get("role"))) {
                    // 如果已有系统消息，追加RAG内容
                    String existingContent = msg.get("content");
                    String languageInstruction = getLanguageInstruction(language);

                    systemContent = existingContent + "\n\n" + languageInstruction + "\n\n" +
                            getReferenceLabel(language) + "\n" + ragContent;
                    msg.put("content", systemContent);
                    break;
                }
            }
        } else {
            // 创建新的系统消息
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemContent);

            messages.add(0, systemMessage);
        }
    }

    /**
     * 根据检测到的语言生成系统内容
     */
    private String generateSystemContent(String ragContent, String language) {
        String baseInstruction = getBaseInstruction(language);
        String languageInstruction = getLanguageInstruction(language);
        String referenceLabel = getReferenceLabel(language);

        return baseInstruction + "\n\n" + languageInstruction + "\n\n" + referenceLabel + "\n" + ragContent;
    }

    /**
     * 获取基础指令
     */
    private String getBaseInstruction(String language) {
        switch (language) {
            case "chinese":
                return "你是 CitaCita 的智能助手。请基于以下相关资料回答用户问题：";
            case "malay":
                return "Anda adalah pembantu pintar CitaCita. Sila jawab soalan pengguna berdasarkan maklumat yang berkaitan berikut:";
            case "english":
            default:
                return "You are CitaCita's intelligent assistant. Please answer user questions based on the following information:";
        }
    }

    /**
     * 获取语言限定指令
     */
    private String getLanguageInstruction(String language) {
        switch (language) {
            case "chinese":
                return "请确保你的回答使用中文。";
            case "malay":
                return "Pastikan jawapan anda menggunakan Bahasa Melayu.";
            case "english":
            default:
                return "Please ensure your answers are in English.";
        }
    }

    /**
     * 获取参考资料标签
     */
    private String getReferenceLabel(String language) {
        switch (language) {
            case "chinese":
                return "[相关资料]";
            case "malay":
                return "[Maklumat Rujukan]";
            case "english":
            default:
                return "[Reference Information]";
        }
    }

    /**
     * Speech-to-Text
     */
    @PostMapping(value = "/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> transcribeBatch(@RequestPart("audio") Mono<FilePart> filePartMono) {
        return azureStreamService.transcribeBatch(filePartMono);
    }

    /**
     * Resume-Polish
     */
    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> resumePolish(@RequestPart("resume") Mono<FilePart> filePartMono) {
        return azureStreamService.resumePolish(filePartMono);
    }

    /**
     * Text-to-Speech
     */
    @PostMapping(value = "/text-to-speech")
    public Mono<ResponseEntity<byte[]>> tts(@RequestPart("text") String text) {
        // 假设 azureStreamService.tts(text) 现在返回 Mono<byte[]>
        return azureStreamService.tts(text).map(audioBytes -> {
            // 设置HTTP响应头
            HttpHeaders headers = new HttpHeaders();
            // 1. 设置内容类型为音频 MPEG (MP3)
            headers.setContentType(MediaType.valueOf("audio/mpeg"));
            // 2. 设置内容长度
            headers.setContentLength(audioBytes.length);
            // 3. (可选) 设置为附件，并指定下载的文件名
            headers.setContentDispositionFormData("attachment", "speech.mp3");

            // 构建并返回ResponseEntity
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(audioBytes);
        });
    }
}
