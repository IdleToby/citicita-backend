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
    
    private final AzureStreamService azureStreamService;
    private final ResumeRagService resumeRagService; // 已经有了，保持不变
    
    public Flux<String> streamChat(ChatRequest request) {
        log.info("=== ResumeChatService 开始处理 ===");
        log.info("界面语言: {}", request.getLanguage());
        
        // 检查RAG状态
        boolean ragEnabled = request.getRagConfig() != null && request.getRagConfig().isEnabled();
        log.info("RAG启用状态: {}", ragEnabled);
        
        if (ragEnabled && request.getRagConfig().getRetrievalContext() != null) {
            log.info("简历文件: {}", request.getRagConfig().getRetrievalContext().getResumeFile());
        }
        
        try {
            Map<String, Object> body = buildAiChatCompatibleBody(request);
            
            return azureStreamService.streamChat(body)
                    .doOnNext(chunk -> {
                        if (chunk.contains("\"content\":\"") && !chunk.contains("\"content\":\"\"")) {
                            log.debug("收到正常内容");
                        }
                    })
                    .doOnComplete(() -> log.info("流响应完成"));
                    
        } catch (Exception e) {
            log.error("ResumeChatService 处理失败: ", e);
            return Flux.just(
                "data: {\"choices\":[{\"delta\":{\"content\":\"处理请求失败\"}}]}\n\n", 
                "data: [DONE]\n\n"
            );
        }
    }
    
    private Map<String, Object> buildAiChatCompatibleBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> cleanMessages = new ArrayList<>();
        
        // 构建增强的系统消息（包含RAG内容）
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        String systemPrompt = buildEnhancedSystemPrompt(request);
        systemMsg.put("content", systemPrompt);
        cleanMessages.add(systemMsg);
        
        log.info("系统提示长度: {}", systemPrompt.length());
        
        // 只保留真正的对话，过滤掉前端生成的长文本
        for (ChatRequest.ChatMessage msg : request.getMessages()) {
            if ("user".equals(msg.getRole())) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("role", msg.getRole());
                msgMap.put("content", msg.getContent());
                cleanMessages.add(msgMap);
            } else if ("assistant".equals(msg.getRole())) {
                String content = msg.getContent();
                if (content != null && 
                    content.length() < 200 && 
                    !content.contains("欢迎使用CitaCita") && 
                    !content.contains("简历分析完成") && 
                    !content.contains("📊 详细分析结果")) {
                    
                    Map<String, Object> msgMap = new HashMap<>();
                    msgMap.put("role", msg.getRole());
                    msgMap.put("content", content);
                    cleanMessages.add(msgMap);
                }
            }
        }
        
        body.put("messages", cleanMessages);
        body.put("model", request.getModel() != null ? request.getModel() : "gpt-oss-120b");
        body.put("stream", true);
        body.put("language", request.getLanguage());
        
        log.info("使用语言: {}, 清理后的消息数量: {} (原始: {})", 
               request.getLanguage(), cleanMessages.size(), request.getMessages().size());
        
        return body;
    }
    
    /**
     * 构建包含RAG内容的增强系统提示
     */
    private String buildEnhancedSystemPrompt(ChatRequest request) {
        String basePrompt = buildSystemPromptByLanguage(request.getLanguage());
        
        // 检查是否启用RAG
        if (request.getRagConfig() != null && request.getRagConfig().isEnabled()) {
            log.info("开始检索RAG上下文");
            String ragContext = retrieveRagContext(request);
            
            if (!ragContext.isEmpty()) {
                log.info("RAG上下文已添加，长度: {}", ragContext.length());
                return basePrompt + "\n\n" + ragContext;
            } else {
                log.warn("RAG启用但未获取到上下文");
            }
        } else {
            log.info("RAG未启用");
        }
        
        return basePrompt;
    }
    
    /**
     * 检索RAG上下文
     */
    private String retrieveRagContext(ChatRequest request) {
        if (request.getRagConfig() == null || 
            request.getRagConfig().getRetrievalContext() == null) {
            log.warn("RAG配置或检索上下文为空");
            return "";
        }
        
        ChatRequest.RagConfig.RetrievalContext context = request.getRagConfig().getRetrievalContext();
        if (context.getResumeFile() == null || context.getResumeFile().trim().isEmpty()) {
            log.warn("简历文件名为空");
            return "";
        }
        
        try {
            return resumeRagService.buildResumeContext(
                context.getResumeFile(),
                context.getUserQuery() != null ? context.getUserQuery() : "",
                request.getLanguage()
            );
        } catch (Exception e) {
            log.error("RAG检索失败", e);
            return "";
        }
    }
    
    /**
     * 根据前端language参数生成对应语言的系统提示
     */
    private String buildSystemPromptByLanguage(String language) {
        switch (language != null ? language : "en") {
            case "zh-CN":
                return "你是CitaCita的简历分析师。请用中文简洁地回答用户关于简历的问题。" +
                       "**重要**: 你只能基于提供的简历分析结果回答，不要编造或猜测任何不在分析结果中的信息。";
            case "en":
                return "You are a resume analyst from CitaCita. Please respond concisely in English to user questions about resumes. " +
                       "**Important**: Only answer based on the provided resume analysis results. Do not make up or guess any information not in the analysis.";
            case "ms":
                return "Anda adalah penganalisis resume dari CitaCita. Sila jawab dengan ringkas dalam Bahasa Melayu untuk soalan pengguna tentang resume. " +
                       "**Penting**: Hanya jawab berdasarkan hasil analisis resume yang disediakan. Jangan buat atau teka sebarang maklumat yang tidak ada dalam analisis.";
            default:
                return "You are a resume analyst from CitaCita. Please respond concisely in English to user questions about resumes. " +
                       "**Important**: Only answer based on the provided resume analysis results.";
        }
    }
}